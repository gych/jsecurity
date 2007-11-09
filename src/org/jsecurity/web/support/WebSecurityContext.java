package org.jsecurity.web.support;

import org.jsecurity.SecurityManager;
import org.jsecurity.context.support.DelegatingSecurityContext;
import org.jsecurity.session.Session;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.net.InetAddress;
import java.util.List;

/**
 * TODO class JavaDoc
 *
 * @author Les Hazlewood
 */
public class WebSecurityContext extends DelegatingSecurityContext {

    private boolean useJSecuritySessions = false;
    private ServletRequest servletRequest = null;

    public WebSecurityContext( boolean authenticated, InetAddress inetAddress, Session session, SecurityManager securityManager, ServletRequest request ) {
        super( authenticated, inetAddress, session, securityManager );
        this.servletRequest = request;
    }

    public WebSecurityContext( Object principal, boolean authenticated, InetAddress inetAddress, Session session, SecurityManager securityManager, ServletRequest request ) {
        super( principal, authenticated, inetAddress, session, securityManager );
        this.servletRequest = request;
    }

    public WebSecurityContext( List<?> principals, boolean authenticated, InetAddress inetAddress, Session session, SecurityManager securityManager, ServletRequest request ) {
        super( principals, authenticated, inetAddress, session, securityManager );
        this.servletRequest = request;
    }

    public boolean isUseJSecuritySessions() {
        return useJSecuritySessions;
    }

    public void setUseJSecuritySessions( boolean useJSecuritySessions ) {
        this.useJSecuritySessions = useJSecuritySessions;
    }

    public Session getSession( boolean create ) {
        if ( isUseJSecuritySessions() ) {
            return super.getSession( create );
        } else {
            assertValid();
            if ( this.session == null ) {
                if ( !( this.servletRequest instanceof HttpServletRequest ) ) {
                    String msg = "The " + getClass().getName() + " implementation currently only works with HttpServletRequests.";
                    throw new IllegalStateException( msg );
                }
                HttpSession httpSession = ( (HttpServletRequest)this.servletRequest ).getSession( create );
                if ( httpSession != null ) {
                    this.session = new WebSession( httpSession, SecurityWebSupport.getInetAddress( this.servletRequest ) );
                }
            }
            return this.session;
        }
    }
}