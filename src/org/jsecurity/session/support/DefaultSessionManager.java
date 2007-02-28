/*
 * Copyright (C) 2005-2007 Les Hazlewood
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the
 *
 * Free Software Foundation, Inc.
 * 59 Temple Place, Suite 330
 * Boston, MA 02111-1307
 * USA
 *
 * Or, you may view it online at
 * http://www.opensource.org/licenses/lgpl-license.php
 */
package org.jsecurity.session.support;

import org.jsecurity.session.ExpiredSessionException;
import org.jsecurity.session.InvalidSessionException;
import org.jsecurity.session.Session;
import org.jsecurity.session.support.quartz.QuartzSessionValidationScheduler;
import org.jsecurity.session.support.eis.ehcache.EhcacheSessionDAO;
import org.jsecurity.session.support.eis.SessionDAO;
import org.jsecurity.util.Destroyable;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Date;

/**
 * Default business-tier implementation of the {@link ValidatingSessionManager} interface.
 *
 * @since 0.1
 * @author Les Hazlewood
 * @author Jeremy Haile
 */
public class DefaultSessionManager extends AbstractSessionManager
        implements ValidatingSessionManager, Destroyable {

    /**
     * Validator used to validate sessions on a regular basis.
     * By default, the session manager will use Quartz to schedule session validation, but this
     * can be overridden by calling {@link #setSessionValidationScheduler(SessionValidationScheduler)}
     */
    protected SessionValidationScheduler sessionValidationScheduler = new QuartzSessionValidationScheduler( this );

    private boolean sessionDAOImplicitlyCreated = false;


    public DefaultSessionManager(){
        setSessionClass( SimpleSession.class );
    }

    public void init() {

        SessionDAO sessionDAO = getSessionDAO();
        if ( sessionDAO == null ) {
            if ( log.isDebugEnabled() ) {
                log.debug( "No sessionDAO set.  Defaulting to EhcacheSessionDAO instance." );
            }
            EhcacheSessionDAO ehcacheSessionDAO = new EhcacheSessionDAO();
            setSessionDAO( ehcacheSessionDAO );
            sessionDAOImplicitlyCreated = true;

            if ( log.isDebugEnabled() ) {
                log.debug( "Initializing EhcacheSessionDAO instance..." );
            }
            ehcacheSessionDAO.init();
        }

        super.init();

        // Start session validation
        if ( sessionValidationScheduler != null ) {

            if( log.isInfoEnabled() ) {
                log.info( "Starting session validation scheduler..." );
            }

            sessionValidationScheduler.startSessionValidation();
        } else {
            if (log.isWarnEnabled()) {
                log.warn("No session validation scheduler is configured, so sessions may not be validated.");
            }
        }
    }

    public void destroy() {
        try {
            if ( sessionValidationScheduler != null ) {
                sessionValidationScheduler.stopSessionValidation();
            }
        } catch (Exception e) {
            if ( log.isWarnEnabled() ) {
                String msg = "Unable to cleanly destroy sessionValidationScheduler [" + sessionValidationScheduler + "].";
                log.warn( msg, e );
            }
        }

        if ( sessionDAOImplicitlyCreated ) {
            if ( sessionDAO instanceof Destroyable ) {
                try {
                    ((Destroyable)sessionDAO).destroy();
                } catch ( Exception e ) {
                    if ( log.isDebugEnabled() ) {
                        log.debug( "Unable to cleanly destroy implicitly created sessionDAO [" + sessionDAO + "]." );
                    }
                }
            }
        }
    }

    public void setSessionValidationScheduler(SessionValidationScheduler sessionValidationScheduler) {
        this.sessionValidationScheduler = sessionValidationScheduler;
    }


    protected void onStop( Session session ) {
        if ( log.isTraceEnabled() ) {
            log.trace( "Updating destroy time of session with id [" + session.getSessionId() + "]" );
        }
        ((SimpleSession)session).setStopTimestamp( new Date() );
    }

    protected void onExpire( Session session ) {
        if ( log.isTraceEnabled() ) {
            log.trace( "Updating destroy time and expiration status of session with id " +
                       session.getSessionId() + "]");
        }
        SimpleSession ss = (SimpleSession)session;
        ss.setStopTimestamp( new Date() );
        ss.setExpired( true );
    }

    protected void onTouch( Session session ) {
        if ( log.isTraceEnabled() ) {
            log.trace( "Updating last access time of session with id [" +
                       session.getSessionId() + "]");
        }
        ((SimpleSession)session).setLastAccessTime( new Date() );
    }

    protected void init( Session newInstance, InetAddress hostAddr ) {
        if ( newInstance instanceof SimpleSession ) {
            SimpleSession ss = (SimpleSession)newInstance;
            ss.setHostAddress( hostAddr );
        }
    }

    /**
     * @see org.jsecurity.session.support.ValidatingSessionManager#validateSessions()
     */
    public void validateSessions() {
        if ( log.isInfoEnabled() ) {
            log.info( "Validating all active sessions..." );
        }

        int invalidCount = 0;

        Collection<Session> activeSessions = getSessionDAO().getActiveSessions();

        if ( activeSessions != null && !activeSessions.isEmpty() ) {
            for( Session s : activeSessions ) {
                try {
                    validate( s );
                } catch ( InvalidSessionException e ) {
                    if ( log.isDebugEnabled() ) {
                        boolean expired = (e instanceof ExpiredSessionException );
                        String msg = "Invalidated session with id [" + s.getSessionId() + "]" +
                            ( expired ? " (expired)" : " (stopped)" );
                        log.debug( msg );
                    }
                    invalidCount++;
                }
            }
        }

        if ( log.isInfoEnabled() ) {
            String msg = "Finished session validation.";
            if ( invalidCount > 0 ) {
                msg += "  [" + invalidCount + "] sessions were stopped.";
            } else {
                msg += "  No sessions were stopped.";
            }
            log.info( msg );
        }
    }

    public void validateSession( Serializable sessionId ) {
        retrieveAndValidateSession( sessionId );
    }

}