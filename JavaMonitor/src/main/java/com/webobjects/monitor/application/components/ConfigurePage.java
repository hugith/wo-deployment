package com.webobjects.monitor.application.components;

import java.util.List;

/*
 (c) Copyright 2006- 2007 Apple Computer, Inc. All rights reserved.

 IMPORTANT:  This Apple software is supplied to you by Apple Computer, Inc. ("Apple") in consideration of your agreement to the following terms, and your use, installation, modification or redistribution of this Apple software constitutes acceptance of these terms.  If you do not agree with these terms, please do not use, install, modify or redistribute this Apple software.

 In consideration of your agreement to abide by the following terms, and subject to these terms, Apple grants you a personal, non-exclusive license, under Apple's copyrights in this original Apple software (the "Apple Software"), to use, reproduce, modify and redistribute the Apple Software, with or without modifications, in source and/or binary forms; provided that if you redistribute the Apple Software in its entirety and without modifications, you must retain this notice and the following text and disclaimers in all such redistributions of the Apple Software.  Neither the name, trademarks, service marks or logos of Apple Computer, Inc. may be used to endorse or promote products derived from the Apple Software without specific prior written permission from Apple.  Except as expressly stated in this notice, no other rights or licenses, express or implied, are granted by Apple herein, including but not limited to any patent rights that may be infringed by your derivative works or by other works in which the Apple Software may be incorporated.

 The Apple Software is provided by Apple on an "AS IS" basis.  APPLE MAKES NO WARRANTIES, EXPRESS OR IMPLIED, INCLUDING WITHOUT LIMITATION THE IMPLIED WARRANTIES OF NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, REGARDING THE APPLE SOFTWARE OR ITS USE AND OPERATION ALONE OR IN COMBINATION WITH YOUR PRODUCTS. 

 IN NO EVENT SHALL APPLE BE LIABLE FOR ANY SPECIAL, INDIRECT, INCIDENTAL OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) ARISING IN ANY WAY OUT OF THE USE, REPRODUCTION, MODIFICATION AND/OR DISTRIBUTION OF THE APPLE SOFTWARE, HOWEVER CAUSED AND WHETHER UNDER THEORY OF CONTRACT, TORT (INCLUDING NEGLIGENCE), STRICT LIABILITY OR OTHERWISE, EVEN IF APPLE HAS BEEN  ADVISED OF THE POSSIBILITY OF 
 SUCH DAMAGE.
 */
import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.monitor._private.MObject;
import com.webobjects.monitor._private.StringExtensions;
import com.webobjects.monitor.application.MonitorComponent;

import er.extensions.appserver.ERXRedirect;

public class ConfigurePage extends MonitorComponent {

	public String backupNote;
	public boolean isAdaptorSettingsSectionVisible = false;
	public boolean isEmailSectionVisible = false;
	public boolean isBackupSectionVisible = false;
	public String _loadSchedulerSelection = null;
	public String loadSchedulerItem;
	public List<String> loadSchedulerList = MObject.LOAD_SCHEDULERS;
	public Integer urlVersionItem;
	public List<Integer> urlVersionList = MObject.URL_VERSIONS;
	public String customSchedulerName;
	public String adaptorInfoUsername;
	public String adaptorInfoPassword;

	public ConfigurePage( WOContext aWocontext ) {
		super( aWocontext );
	}

	public WOComponent HTTPServerUpdateClicked() {
		handler().sendUpdateSiteToWotaskds();
		return ConfigurePage.create( context() );
	}

	public WOComponent emailUpdateClicked() {
		handler().sendUpdateSiteToWotaskds();
		return ConfigurePage.create( context() );
	}

	public String loadSchedulerSelection() {
		if( (application() != null) && (siteConfig().scheduler() != null) ) {
			int indexOfScheduler = MObject.LOAD_SCHEDULER_VALUES.indexOf( siteConfig().scheduler() );
			if( indexOfScheduler != -1 ) {
				_loadSchedulerSelection = loadSchedulerList.get( indexOfScheduler );
			}
			else {
				// Custom scheduler
				_loadSchedulerSelection = loadSchedulerList.get( loadSchedulerList.size() - 1 );
				customSchedulerName = siteConfig().scheduler();
			}
		}
		return _loadSchedulerSelection;
	}

	public void setLoadSchedulerSelection( String value ) {
		_loadSchedulerSelection = value;
	}

	public Integer urlVersionSelection() {
		if( application() != null ) {
			return siteConfig().urlVersion();
		}

		return null;
	}

	public void setUrlVersionSelection( Integer value ) {
		if( application() != null ) {
			siteConfig().setUrlVersion( value );
		}
	}

	public WOComponent adaptorUpdateClicked() {
		String newValue;

		int i = loadSchedulerList.indexOf( _loadSchedulerSelection );
		if( i == 0 ) {
			newValue = null;
		}
		else if( i == (loadSchedulerList.size() - 1) ) {
			newValue = customSchedulerName;
			if( !StringExtensions.isValidXMLString( newValue ) ) {
				newValue = null;
			}
		}
		else {
			newValue = MObject.LOAD_SCHEDULER_VALUES.get( i );
		}
		siteConfig().setScheduler( newValue );

		handler().sendUpdateSiteToWotaskds();

		ConfigurePage aPage = ConfigurePage.create( context() );
		return aPage;
	}

	public WOComponent backupConfiguration() {
		siteConfig().forceBackup( backupNote );
		return context().page();
	}

	public static ConfigurePage create( WOContext context ) {
		return (ConfigurePage)context.page().pageWithName( ConfigurePage.class.getName() );
	}

	public WOActionResults adaptorInfoLoginClicked() {
		String url = siteConfig().woAdaptor() + "/WOAdaptorInfo?" + adaptorInfoUsername + "+" + adaptorInfoPassword;

		if( url.startsWith( "http://" ) ) {
			url = url.replaceFirst( "http://", "https://" );
		}

		ERXRedirect redirect = pageWithName( ERXRedirect.class );
		redirect.setUrl( url );
		return redirect;
	}
}