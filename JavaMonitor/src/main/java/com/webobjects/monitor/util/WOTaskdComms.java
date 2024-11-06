package com.webobjects.monitor.util;

import java.util.ArrayList;
import java.util.List;

import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.monitor._private.CoderWrapper;
import com.webobjects.monitor._private.MHost;
import com.webobjects.monitor._private.MSiteConfig;

import x.ResponseWrapper;

public class WOTaskdComms {

	/**
	 * Communications Goop
	 */
	public static ResponseWrapper[] sendRequestToWotaskdArray( final String contentString, final List<MHost> hosts, final boolean willChange ) {

		final MHost aHost = hosts.get( 0 );

		// FIXME: A little danger sign here... // Hugi 2024-11-02
		if( aHost == null ) {
			return null;
		}

		final MSiteConfig siteConfig = aHost.siteConfig();

		// we had errors reaching a host last time - do it again!
		if( !siteConfig.hostErrorArray.isEmpty() ) {
			syncHostsWithErrors( siteConfig );
		}

		final Thread[] workers = new Thread[hosts.size()];
		final ResponseWrapper[] responses = new ResponseWrapper[workers.length];

		for( int i = 0; i < workers.length; i++ ) {
			final int j = i;

			Runnable work = new Runnable() {
				@Override
				public void run() {
					final MHost host = hosts.get( j );
					responses[j] = host.sendRequestToWotaskd( contentString, siteConfig.passwordDictionary(), willChange, false );
				}
			};

			workers[j] = new Thread( work );
			workers[j].start();
		}

		try {
			for( int i = 0; i < workers.length; i++ ) {
				workers[i].join();
			}
		}
		catch( InterruptedException ie ) {
			// might be bad?
		}

		return responses;
	}

	private static void syncHostsWithErrors( final MSiteConfig siteConfig ) {
		final List<MHost> hosts = new ArrayList<>( siteConfig.hostErrorArray );

		if( NSLog.debugLoggingAllowedForLevelAndGroups( NSLog.DebugLevelDetailed, NSLog.DebugGroupDeployment ) ) {
			NSLog.debug.appendln( "Sending sync requests to: " + hosts );
		}

		final Thread[] workers = new Thread[hosts.size()];

		for( int i = 0; i < workers.length; i++ ) {
			final int j = i;

			final Runnable work = new Runnable() {
				@Override
				public void run() {
					MHost host = hosts.get( j );
					host.sendRequestToWotaskd( syncRequestContent( siteConfig ), siteConfig.passwordDictionary(), true, true );
				}
			};

			workers[j] = new Thread( work );
			workers[j].start();
		}

		try {
			for( int i = 0; i < workers.length; i++ ) {
				workers[i].join();
			}
		}
		catch( InterruptedException ie ) {}
	}

	private static String syncRequestContent( final MSiteConfig siteConfig ) {
		final NSMutableDictionary<String, NSDictionary> data = new NSMutableDictionary<>( siteConfig.dictionaryForArchive(), "SiteConfig" );
		final NSMutableDictionary<String, NSMutableDictionary<String, NSDictionary>> updateWotaskd = new NSMutableDictionary<String, NSMutableDictionary<String, NSDictionary>>( data, "sync" );
		final NSMutableDictionary<String, NSMutableDictionary<String, NSMutableDictionary<String, NSDictionary>>> monitorRequest = new NSMutableDictionary<String, NSMutableDictionary<String, NSMutableDictionary<String, NSDictionary>>>( updateWotaskd, "updateWotaskd" );
		final String syncRequestString = new CoderWrapper().encodeRootObjectForKey( monitorRequest, "monitorRequest" );
		return syncRequestString;
	}
}