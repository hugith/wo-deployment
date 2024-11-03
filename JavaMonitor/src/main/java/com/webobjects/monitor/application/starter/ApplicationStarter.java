package com.webobjects.monitor.application.starter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableSet;
import com.webobjects.monitor._private.MApplication;
import com.webobjects.monitor.util.WOTaskdHandler;
import com.webobjects.monitor.util.WOTaskdHandler.ErrorCollector;

/**
 * Bounces an application. 
 *
 * @author ak
 */
public abstract class ApplicationStarter extends Thread implements ErrorCollector {

	private MApplication _app;

	private WOTaskdHandler _handler;

	private Set<String> _errors;

	private String _status;

	public ApplicationStarter( MApplication app ) {
		_app = app;
		_handler = new WOTaskdHandler( this );
		setName( "Bouncer: " + app.name() );
	}

	protected abstract void bounce() throws InterruptedException;

	protected void log( Object msg ) {
		NSLog.out.appendln( msg );
		_status = msg != null ? msg.toString() : "No status";
	}

	public WOTaskdHandler handler() {
		return _handler;
	}

	public MApplication application() {
		return _app;
	}

	@Override
	public void run() {
		try {
			_errors = new NSMutableSet<>();
			bounce();
		}
		catch( InterruptedException e ) {
			log( e );
		}
	}

	public synchronized void addObjectsFromArrayIfAbsentToErrorMessageArray( final List<String> errors ) {
		_errors.addAll( errors );
	}

	public synchronized List<String> errors() {
		return new ArrayList<>( _errors );
	}

	@Override
	public String toString() {
		return "Bouncer: " + _app.name() + "->" + _status;
	}

	public String status() {
		return _status;
	}
}