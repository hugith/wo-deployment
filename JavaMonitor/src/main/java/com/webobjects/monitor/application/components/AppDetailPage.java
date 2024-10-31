package com.webobjects.monitor.application.components;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver._private.WOProperties;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableSet;
import com.webobjects.monitor._private.MApplication;
import com.webobjects.monitor._private.MHost;
import com.webobjects.monitor._private.MInstance;
import com.webobjects.monitor._private.MObject;
import com.webobjects.monitor.application.MonitorComponent;
import com.webobjects.monitor.application.components.ConfirmationPage.ConfirmationDelegate;
import com.webobjects.monitor.application.starter.ApplicationStarter;
import com.webobjects.monitor.application.starter.GracefulBouncer;
import com.webobjects.monitor.application.starter.RollingShutdownBouncer;
import com.webobjects.monitor.application.starter.ShutdownBouncer;
import com.webobjects.monitor.util.StatsUtilities;

import er.extensions.appserver.ERXApplication;

public class AppDetailPage extends MonitorComponent {

	public MInstance currentInstance;
	public boolean isClearDeathSectionVisible;
	public boolean showDetailStatistics;

	public MHost currentHost;
	public MHost selectedHost;
	public int numberOfInstancesToAdd = 1;
	public String instanceNameFilterValue;

	private String _hrefToApp;

	private NSArray<MInstance> _allInstances = new NSArray<>();
	private NSArray<MInstance> _selectedInstances = new NSArray<>();
	
	public String filterErrorMessage;

	public AppDetailPage( WOContext aWocontext ) {
		super( aWocontext );
		handler().updateForPage( getClass() );
	}

	public WOComponent showStatisticsClicked() {
		showDetailStatistics = !showDetailStatistics;
		return context().page();
	}

	public WOComponent refreshClicked() {
		return newDetailPage();
	}

	private String bouncerName() {
		return "Bouncer." + myApplication().name();
	}

	public ApplicationStarter currentBouncer() {
		return (ApplicationStarter)session().objectForKey( bouncerName() );
	}

	public WOComponent bounceClicked() {
		return bounceClickedWithGracefulBouncer();
	}

	public WOComponent bounceClickedWithGracefulBouncer() {
		return bounceClickedWithBouncer( new GracefulBouncer( myApplication() ) );
	}

	public WOComponent bounceClickedWithShutdownBouncer( int maxwait ) {
		return bounceClickedWithBouncer( new ShutdownBouncer( myApplication(), maxwait ) );
	}

	public WOComponent bounceClickedWithRollingBouncer() {
		return bounceClickedWithBouncer( new RollingShutdownBouncer( myApplication() ) );
	}

	private WOComponent bounceClickedWithBouncer( ApplicationStarter bouncer ) {
		ApplicationStarter old = currentBouncer();
		if( old != null ) {
			old.interrupt();
		}
		session().setObjectForKey( bouncer, bouncerName() );
		bouncer.start();
		return newDetailPage();
	}

	public MInstance currentInstance() {
		return currentInstance;
	}

	public WOActionResults selectAllAction() {
		setSelectedInstances( allInstances() );
		return null;
	}

	public WOActionResults selectNoneAction() {
		setSelectedInstances( new NSMutableArray() );
		return null;
	}

	public void selectRunning() {
		final NSMutableArray<MInstance> selected = new NSMutableArray<>();

		for( final MInstance instance : allInstances() ) {
			if( instance.isRunning_M() ) {
				selected.addObject( instance );
			}
		}

		setSelectedInstances( selected );
	}

	public void selectNotRunning() {
		final NSMutableArray<MInstance> selected = new NSMutableArray<>();

		for( final MInstance instance : allInstances() ) {
			if( !instance.isRunning_M() ) {
				selected.addObject( instance );
			}
		}

		setSelectedInstances( selected );
	}

	public WOActionResults selectInstanceNamesMatchingFilter() {
		filterErrorMessage = null;
		final NSMutableArray<MInstance> selected = new NSMutableArray<>();

		if( instanceNameFilterValue != null ) {
			try {
				final Pattern p = Pattern.compile( instanceNameFilterValue );

				for( final MInstance instance : allInstances() ) {
					final Matcher matcherForInstanceName = p.matcher( instance.displayName() );

					if( matcherForInstanceName.matches() ) {
						selected.addObject( instance );
					}
				}
			}
			catch( java.util.regex.PatternSyntaxException pse ) {
				if( pse.getMessage() != null ) {
					filterErrorMessage = pse.getMessage();
				}
				else {
					filterErrorMessage = "PatternSyntaxException";
				}
			}

			setSelectedInstances( selected );
		}
		return null;
	}

	public void selectOne() {
		_setIsSelectedInstance( !isSelectedInstance() );
	}

	public void _setIsSelectedInstance( boolean selected ) {
		final NSMutableArray selectedObjects = selectedInstances().mutableClone();

		if( selected && !selectedObjects.containsObject( currentInstance ) ) {
			selectedObjects.addObject( currentInstance );
		}
		else if( !selected && selectedObjects.containsObject( currentInstance ) ) {
			selectedObjects.removeObject( currentInstance );
		}

		setSelectedInstances( selectedObjects );
	}

	public void setIsSelectedInstance( boolean selected ) {

	}

	public boolean isSelectedInstance() {
		return selectedInstances().contains( currentInstance );
	}

	public boolean hasInstances() {
		final NSArray instancesArray = myApplication().instanceArray();

		if( instancesArray == null || instancesArray.count() == 0 ) {
			return false;
		}

		return true;
	}

	public boolean isRefreshEnabled() {
		final NSArray instancesArray = myApplication().instanceArray();

		if( instancesArray == null || instancesArray.count() == 0 ) {
			return false;
		}

		return siteConfig().viewRefreshEnabled().booleanValue();
	}

	public WOComponent configureApplicationClicked() {
		AppConfigurePage aPage = AppConfigurePage.create( context(), myApplication() );
		aPage.isNewInstanceSectionVisible = true;
		return aPage;
	}

	public WOComponent instanceDetailClicked() {
		return InstDetailPage.create( context(), currentInstance );
	}

	public WOComponent configureInstanceClicked() {
		return InstConfigurePage.create( context(), currentInstance );
	}

	public WOComponent deleteInstanceClicked() {

		final MInstance instance = currentInstance;

		return ConfirmationPage.create( context(), new ConfirmationDelegate(
				APP_PAGE,
				"Are you sure you want to delete this instance (" + instance.displayName() + " running on " + instance.hostName() + ")",
				"Selecting 'Yes' will shutdown the selected instance of this application and delete its instance configuration.",
				() -> {
					handler().startWriting();
					try {
						siteConfig().removeInstance_M( instance );

						if( siteConfig().hostArray().count() != 0 ) {
							handler().sendRemoveInstancesToWotaskds( new NSArray( instance ), siteConfig().hostArray() );
						}
					}
					finally {
						handler().endWriting();
					}
					return AppDetailPage.create( context(), instance.application() );
				},
				() -> AppDetailPage.create( context(), instance.application() ) ) );
	}

	public String linkToWOStats() {
		String adaptorURL = siteConfig().woAdaptor();
		StringBuffer aURL = null;
		if( adaptorURL != null ) {
			// using adaptor URL
			aURL = new StringBuffer( hrefToInst() );
		}
		else {
			// direct connect
			aURL = new StringBuffer( hrefToInstDirect() );
			aURL = aURL.append( "/cgi-bin/WebObjects/" );
			aURL = aURL.append( myApplication().name() );
			aURL = aURL.append( ".woa" );
		}
		aURL = aURL.append( "/wa/ERXDirectAction/stats?pw=" + WOProperties.TheStatisticsStorePassword );
		return aURL.toString();
	}

	public String hrefToApp() {
		if( _hrefToApp == null ) {
			String adaptorURL = siteConfig().woAdaptor();
			if( adaptorURL == null ) {
				adaptorURL = WOApplication.application().cgiAdaptorURL();
			}
			if( adaptorURL.charAt( adaptorURL.length() - 1 ) == '/' ) {
				_hrefToApp = adaptorURL + myApplication().name();
			}
			else {
				_hrefToApp = adaptorURL + "/" + myApplication().name();
			}
		}
		return _hrefToApp;
	}

	public String hrefToInst() {
		return hrefToApp() + ".woa/" + currentInstance.id();
	}

	public String hrefToInstDirect() {
		return "http://" + currentInstance.hostName() + ":" + currentInstance.port();
	}

	public boolean shouldDisplayDeathDetailLink() {
		return currentInstance.deathCount() > 0;
	}

	public WOComponent instanceDeathDetailClicked() {
		return AppDeathPage.create( context(), currentInstance );
	}

	public WOComponent clearAllDeathsClicked() {
		handler().startReading();
		try {
			if( myApplication().hostArray().count() != 0 ) {
				handler().sendClearDeathsToWotaskds( myApplication().instanceArray(), myApplication().hostArray() );
			}
		}
		finally {
			handler().endReading();
		}

		return newDetailPage();
	}

	public WOComponent startInstance() {

		if( (currentInstance.state == MObject.DEAD) || (currentInstance.state == MObject.STOPPING) || (currentInstance.state == MObject.CRASHING) || (currentInstance.state == MObject.UNKNOWN) ) {
			handler().sendStartInstancesToWotaskds( new NSArray( currentInstance ), new NSArray( currentInstance.host() ) );
			currentInstance.state = MObject.STARTING;
		}

		return newDetailPage();
	}

	public WOComponent stopInstance() {

		if( (currentInstance.state == MObject.ALIVE) || (currentInstance.state == MObject.STARTING) ) {
			handler().sendStopInstancesToWotaskds( new NSArray( currentInstance ), new NSArray( currentInstance.host() ) );
			currentInstance.state = MObject.STOPPING;
		}

		return newDetailPage();
	}

	public WOComponent toggleAutoRecover() {
		if( isTrueNullSafe( currentInstance.autoRecover() ) ) {
			currentInstance.setAutoRecover( Boolean.FALSE );
		}
		else {
			currentInstance.setAutoRecover( Boolean.TRUE );
		}

		sendUpdateInstances( new NSArray( currentInstance ) );

		return newDetailPage();
	}

	private void sendUpdateInstances( NSArray<MInstance> instances ) {
		handler().startReading();

		try {
			final NSMutableSet<MHost> hosts = new NSMutableSet<>();

			for( MInstance instance : instances ) {
				hosts.addObject( instance.host() );
			}

			handler().sendUpdateInstancesToWotaskds( instances, hosts.allObjects() );
		}
		finally {
			handler().endReading();
		}
	}

	public WOComponent toggleRefuseNewSessions() {
		handler().sendRefuseSessionToWotaskds( new NSArray( currentInstance ), new NSArray( currentInstance.host() ), !currentInstance.isRefusingNewSessions() );

		return newDetailPage();
	}

	public WOComponent toggleScheduling() {
		if( isTrueNullSafe( currentInstance.schedulingEnabled() ) ) {
			currentInstance.setSchedulingEnabled( Boolean.FALSE );
		}
		else {
			currentInstance.setSchedulingEnabled( Boolean.TRUE );
		}
		sendUpdateInstances( new NSArray( currentInstance ) );

		return newDetailPage();
	}

	public NSArray<MInstance> allInstances() {
		return _allInstances;
	}
	
	private void setAllInstances( NSArray<MInstance> value ) {
		_allInstances = value;
	}

	private NSArray<MInstance> selectedInstances() {
		return _selectedInstances;
	}
	
	private void setSelectedInstances( NSArray<MInstance> value ) {
		_selectedInstances = value;
	}

	public NSArray<MInstance> runningInstances() {
		return myApplication().runningInstances_M();
	}

	public WOComponent startAllClicked() {

		handler().startReading();

		try {
			startInstances( selectedInstances() );
		}
		finally {
			handler().endReading();
		}

		return newDetailPage();
	}

	private void startInstances( NSArray<MInstance> possibleInstances ) {
		final NSMutableArray<MInstance> instances = new NSMutableArray<>();

		for( MInstance anInstance : possibleInstances ) {
			if( (anInstance.state == MObject.DEAD) || (anInstance.state == MObject.STOPPING) || (anInstance.state == MObject.CRASHING) || (anInstance.state == MObject.UNKNOWN) ) {
				instances.addObject( anInstance );
			}
		}

		if( instances.count() != 0 ) {
			handler().sendStartInstancesToWotaskds( instances, myApplication().hostArray() );

			for( MInstance anInstance : instances ) {
				if( anInstance.state != MObject.ALIVE ) {
					anInstance.state = MObject.STARTING;
				}
			}
		}
	}

	public WOComponent stopAllClicked() {

		final NSArray instances = selectedInstances().immutableClone();
		final MApplication application = myApplication();

		return ConfirmationPage.create( context(), new ConfirmationDelegate(
				APP_PAGE,
				"Are you sure you want to stop the " + instances.count() + " instances of " + application.name() + "?",
				"Selecting 'Yes' will shutdown the selected instances of this application.",
				() -> {
					handler().startReading();
					try {
						if( application.hostArray().count() != 0 ) {
							handler().sendStopInstancesToWotaskds( instances, application.hostArray() );
						}

						for( int i = 0; i < instances.count(); i++ ) {
							final MInstance anInst = (MInstance)instances.objectAtIndex( i );

							if( anInst.state != MObject.DEAD ) {
								anInst.state = MObject.STOPPING;
							}
						}
					}
					finally {
						handler().endReading();
					}
					return AppDetailPage.create( context(), application, instances );
				},
				() -> AppDetailPage.create( context(), application, instances ) ) );
	}

	public WOComponent deleteAllInstancesClicked() {

		final NSArray instances = selectedInstances().immutableClone();
		final MApplication application = myApplication();

		return ConfirmationPage.create( context(), new ConfirmationDelegate(
				APP_PAGE,
				"Are you sure you want to delete the selected <i>" + instances.count() + "</i> instances of application " + application.name() + "?",
				"Selecting 'Yes' will shutdown any shutdown the selected instances of this application, and delete all matching instance configurations.",
				() -> {
					handler().startWriting();
					try {
						siteConfig().removeInstances_M( application, instances );
						
						if( siteConfig().hostArray().count() != 0 ) {
							handler().sendRemoveInstancesToWotaskds( instances, siteConfig().hostArray() );
						}
					}
					finally {
						handler().endWriting();
					}
					return AppDetailPage.create( context(), application, instances );
				},
				() -> AppDetailPage.create( context(), application, instances ) ) );
	}

	public WOComponent autoRecoverEnableAllClicked() {

		handler().startReading();

		try {
			final NSArray instancesArray = selectedInstances();

			for( int i = 0; i < instancesArray.count(); i++ ) {
				MInstance anInst = (MInstance)instancesArray.objectAtIndex( i );
				anInst.setAutoRecover( Boolean.TRUE );
			}

			handler().sendUpdateInstancesToWotaskds( instancesArray, allHosts() );
		}
		finally {
			handler().endReading();
		}

		return newDetailPage();
	}

	public WOComponent autoRecoverDisableAllClicked() {

		handler().startReading();

		try {
			final NSArray instancesArray = selectedInstances();

			for( int i = 0; i < instancesArray.count(); i++ ) {
				MInstance anInst = (MInstance)instancesArray.objectAtIndex( i );
				anInst.setAutoRecover( Boolean.FALSE );
			}

			handler().sendUpdateInstancesToWotaskds( instancesArray, allHosts() );
		}
		finally {
			handler().endReading();
		}

		return newDetailPage();
	}

	public WOComponent acceptNewSessionsAllClicked() {

		handler().startReading();
		
		try {
			handler().sendRefuseSessionToWotaskds( selectedInstances(), myApplication().hostArray(), false );
		}
		finally {
			handler().endReading();
		}
		
		return newDetailPage();
	}

	public WOComponent refuseNewSessionsAllClicked() {
		
		handler().startReading();
		
		try {
			handler().sendRefuseSessionToWotaskds( selectedInstances(), myApplication().hostArray(), true );

			@SuppressWarnings("unused")
			NSArray instancesArray = selectedInstances();
		}
		finally {
			handler().endReading();
		}

		return newDetailPage();
	}

	public WOComponent schedulingEnableAllClicked() {
		
		handler().startReading();

		try {
			NSArray instancesArray = selectedInstances();

			for( int i = 0; i < instancesArray.count(); i++ ) {
				MInstance anInst = (MInstance)instancesArray.objectAtIndex( i );
				anInst.setSchedulingEnabled( Boolean.TRUE );
			}

			if( allHosts().size() != 0 ) {
				handler().sendUpdateInstancesToWotaskds( instancesArray, allHosts() );
			}
		}
		finally {
			handler().endReading();
		}

		return newDetailPage();
	}

	private WOComponent newDetailPage() {
		final AppDetailPage nextPage = AppDetailPage.create( context(), myApplication() );
		nextPage.setSelectedInstances( selectedInstances() );
		nextPage.showDetailStatistics = showDetailStatistics;

		if( currentBouncer() != null && !"Finished".equals( currentBouncer().status() ) && !currentBouncer().errors().isEmpty() ) {
			session().addObjectsFromArrayIfAbsentToErrorMessageArray( currentBouncer().errors() );
			session().removeObjectForKey( bouncerName() );
		}

		return nextPage;
	}

	public WOComponent schedulingDisableAllClicked() {

		handler().startReading();

		try {
			final NSArray instancesArray = selectedInstances();

			for( int i = 0; i < instancesArray.count(); i++ ) {
				MInstance anInst = (MInstance)instancesArray.objectAtIndex( i );
				anInst.setSchedulingEnabled( Boolean.FALSE );
			}

			handler().sendUpdateInstancesToWotaskds( instancesArray, allHosts() );
		}
		finally {
			handler().endReading();
		}

		return newDetailPage();
	}

	public String instanceStatusImage() {
		return switch( currentInstance.state ) {
			case MObject.DEAD -> "PowerSwitch_Off.gif";
			case MObject.ALIVE -> "PowerSwitch_On.gif";
			case MObject.STOPPING -> "Turning_Off.gif";
			case MObject.CRASHING -> "Turning_Off.gif";
			case MObject.STARTING -> "Turning_On.gif";
			default -> throw new IllegalStateException( "Unknown instance state: " + currentInstance.state );
		};
	}

	public String instanceStatusImageText() {
		return switch( currentInstance.state ) {
			case MObject.DEAD -> "OFF";
			case MObject.ALIVE -> "ON";
			case MObject.STOPPING -> "STOPPING";
			case MObject.CRASHING -> "CRASHING";
			case MObject.STARTING -> "STARTING";
			default -> throw new IllegalStateException( "Unknown instance state: " + currentInstance.state );
		};
	}

	public String autoRecoverLabel() {

		if( isTrueNullSafe( currentInstance.autoRecover() ) ) {
			return "On";
		}

		return "Off";
	}

	public String autoRecoverDivClass() {
		String base = "AppControl";
		String results = base + " " + base + "AutoRecoverOff";

		if( isTrueNullSafe( currentInstance.autoRecover() ) ) {
			results = base + " " + base + "AutoRecoverOn";
		}

		return results;
	}

	/**
	 * FIXME: We're going to have to take into account those scheduling classes // Hugi 2024-10-25 
	 */
	public String refuseNewSessionsClass() {
		String base = "AppControl";
		String result = base + " " + base + "NotRefusingNewSessions";

		if( isTrueNullSafe( currentInstance.schedulingEnabled() ) ) {
			if( currentInstance.isRefusingNewSessions() ) {
				result = base + " " + base + "ScheduleEnabledRefusingNewSessions";
			}
			else {
				result = base + " " + base + "ScheduleEnabledNotRefusingNewSessions";
			}
		}
		else {
			if( currentInstance.isRefusingNewSessions() ) {
				result = base + " " + base + "RefusingNewSessions";
			}
		}

		return result;
	}

	public String refuseNewSessionsLabel() {
		return currentInstance.isRefusingNewSessions() ?"On" : "Off";
	}

	public String schedulingLabel() {
		return isTrueNullSafe( currentInstance.schedulingEnabled() ) ? "On" : "Off";
	}

	public String schedulingDivClass() {
		String base = "AppControl";
		String result = base + " " + base + "ScheduleOff";

		if( isTrueNullSafe( currentInstance.schedulingEnabled() ) ) {
			result = base + " " + base + "ScheduleOn";
		}

		return result;
	}

	public String nextShutdown() {
		String result = "N/A";

		if( isTrueNullSafe( currentInstance.schedulingEnabled() ) ) {
			result = currentInstance.nextScheduledShutdownString();
		}

		return result;
	}

	/**
	 * @return Value of the boolean, false if null
	 */
	private static boolean isTrueNullSafe( final Boolean value ) {

		if( value == null ) {
			return false;
		}
		
		return value.booleanValue();
	}

	public Integer totalTransactions() {
		return StatsUtilities.totalTransactionsForApplication( myApplication() );
	}

	public Integer totalTransactionsForActiveInstances() {
		return StatsUtilities.totalTransactionsForActiveInstancesOfApplication( myApplication() );
	}

	public Integer totalActiveSessions() {
		return StatsUtilities.totalActiveSessionsForApplication( myApplication() );
	}

	public Integer totalActiveSessionsForActiveInstances() {
		return StatsUtilities.totalActiveSessionsForActiveInstancesOfApplication( myApplication() );
	}

	public Float totalAverageTransaction() {
		return StatsUtilities.totalAverageTransactionForApplication( myApplication() );
	}

	public Float totalAverageIdleTime() {
		return StatsUtilities.totalAverageIdleTimeForApplication( myApplication() );
	}

	public Float actualRatePerSecond() {
		return StatsUtilities.actualTransactionsPerSecondForApplication( myApplication() );
	}

	public Float actualRatePerMinute() {
		Float aNumber = StatsUtilities.actualTransactionsPerSecondForApplication( myApplication() );
		return Float.valueOf( (aNumber.floatValue() * 60) );
	}

	public WOComponent hostsPageClicked() {
		return HostsPage.create( context() );
	}

	public WOComponent addInstanceClicked() {

		if( numberOfInstancesToAdd < 1 ) {
			return newDetailPage();
		}

		handler().startWriting();
		try {
			NSMutableArray newInstanceArray = siteConfig().addInstances_M( selectedHost, myApplication(), numberOfInstancesToAdd );

			if( allHosts().size() != 0 ) {
				handler().sendAddInstancesToWotaskds( newInstanceArray, allHosts() );
			}
		}
		finally {
			handler().endWriting();
		}

		return newDetailPage();
	}

	public boolean hasHosts() {
		handler().startReading();
		try {
			List<MHost> hosts = allHosts();
			return (hosts != null && (hosts.size() > 0));
		}
		finally {
			handler().endReading();
		}
	}

	/**
	 * @return A new page instance
	 */
	public static AppDetailPage create( final WOContext context, final MApplication mApplication, final NSArray<MInstance> selected ) {
		final AppDetailPage page = ERXApplication.erxApplication().pageWithName( AppDetailPage.class, context );
		page.setMyApplication( mApplication );

		final NSMutableArray<MInstance> instances = new NSMutableArray<>( mApplication.instanceArray() );
		Collections.sort( instances, Comparator.comparing( MInstance::id ) );

		// AK: the MInstances don't really support equals()...
		// FIXME: Why are we...? // Hugi 2024-10-30
		if( !page.allInstances().equals( instances ) ) {
			page.setAllInstances( instances );
		}

		if( selected != null ) {
			final NSMutableArray<MInstance> active = new NSMutableArray<>();

			for( MInstance instance : selected ) {
				if( instances.containsObject( instance ) ) {
					active.addObject( instance );
				}
			}

			page.setSelectedInstances( active );
		}
		else {
			page.setSelectedInstances( page.allInstances() );
		}

		return page;
	}

	/**
	 * FIXME: Ouch.... // Hugi 2024-10-26
	 */
	public static AppDetailPage create( WOContext context, MApplication currentApplication ) {
		NSArray selected = (context.page() instanceof AppDetailPage ? ((AppDetailPage)context.page()).selectedInstances() : null);
		return create( context, currentApplication, selected );
	}
}