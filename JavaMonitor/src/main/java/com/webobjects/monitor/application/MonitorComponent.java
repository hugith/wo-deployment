package com.webobjects.monitor.application;

import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.monitor._private.MApplication;
import com.webobjects.monitor._private.MHost;
import com.webobjects.monitor._private.MInstance;
import com.webobjects.monitor._private.MSiteConfig;
import com.webobjects.monitor.util.WOTaskdHandler;

import er.extensions.components.ERXComponent;

public abstract class MonitorComponent extends ERXComponent {

	public final int APP_PAGE = 0;
	public final int HOST_PAGE = 1;
	public final int SITE_PAGE = 2;
	public final int PREF_PAGE = 3;
	public final int HELP_PAGE = 4;
	public final int MOD_PROXY_PAGE = 6;

	private WOTaskdHandler _handler;
	private MApplication _myApplication;
	private MInstance _myInstance;
	private MHost _myHost;
	private String _message;

	public MonitorComponent( WOContext aWocontext ) {
		super( aWocontext );
		_handler = new WOTaskdHandler( session() );
	}

	@Override
	public void awake() {
		super.awake();
		_message = null;
	}

	public String message() {
		if( _message == null ) {
			_message = session().message();
		}
		return _message;
	}

	public NSMutableArray allHosts() {
		return siteConfig().hostArray();
	}

	public MSiteConfig siteConfig() {
		return WOTaskdHandler.siteConfig();
	}
	
	public Application application() {
		return (Application)super.application();
	}

	public Session session() {
		return (Session)super.session();
	}

	public WOTaskdHandler handler() {
		return _handler;
	}

	public final MApplication myApplication() {
		return _myApplication;
	}

	public void setMyApplication( MApplication application ) {
		assert application != null;
		_myApplication = application;
		_myInstance = null;
	}

	public final MInstance myInstance() {
		return _myInstance;
	}

	public void setMyInstance( MInstance instance ) {
		assert instance != null;
		_myInstance = instance;
		_myApplication = instance.application();
	}

	public final MHost myHost() {
		return _myHost;
	}

	public void setMyHost( MHost host ) {
		_myHost = host;
	}
}