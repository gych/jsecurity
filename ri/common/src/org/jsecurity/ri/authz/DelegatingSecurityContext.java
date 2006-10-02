/*
 * Copyright (C) 2005 Les Hazlewood, Jeremy Haile
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
package org.jsecurity.ri.authz;

import org.jsecurity.context.SecurityContext;
import org.jsecurity.authz.AuthorizationException;
import org.jsecurity.authz.NoSuchPrincipalException;
import org.jsecurity.realm.Realm;
import org.jsecurity.ri.realm.RealmManager;
import org.jsecurity.ri.util.ThreadContext;
import org.jsecurity.session.Session;

import java.security.Permission;
import java.security.Principal;
import java.util.*;

/**
 * Simple implementation of the <tt>SecurityContext</tt> interface that delegates all
 * method calls to an underlying {@link Realm Realm} instance for security checks.  It is
 * essentially a <tt>Realm</tt> proxy.
 *
 * <p>This implementation does not maintain state such as roles and permissions (only a subject
 * identifier, such as a user primary key or username) for better performance in a stateless
 * architecture.  It instead asks the underlying <tt>Realm</tt> every time to perform
 * the authorization check.
 *
 * <p>A common misconception in using this implementation is that an EIS resource (RDBMS, etc) would
 * be &quot;hit&quot; every time a method is called.  This is not necessarily the case and is
 * up to the implementation of the underlying <tt>Realm</tt> instance.  If caching of authorization
 * context data is desired (to eliminate EIS round trips and therefore improve database
 * performance), it is considered much more
 * elegant to let the underlying Realm implementation manage caching, not this class.  A Realm is
 * considered a business-tier component, where caching strategies are better managed.
 *
 * <p>Applications from large and clustered to simple and vm local all benefit from
 * stateless architectures.  This implementation plays a part in the stateless programming
 * paradigm and should be used whenever possible.
 *
 * @since 0.1
 * @author Les Hazlewood
 * @author Jeremy Haile
 */
public class DelegatingSecurityContext implements SecurityContext {

    protected List<Principal> principals;

    protected RealmManager realmManager;

    public DelegatingSecurityContext() {
        principals = new ArrayList<Principal>();
    }

    public DelegatingSecurityContext( Principal subjectIdentifier, RealmManager realmManager ) {
        this.principals = new ArrayList<Principal>(1);
        this.principals.add( subjectIdentifier );
        this.realmManager = realmManager;
    }

    public DelegatingSecurityContext(List<Principal> principals, RealmManager realmManager) {
        this.principals = principals;
        this.realmManager = realmManager;
    }

    /**
     * If multiple principals are defined, this method will return the first
     * principal in the list of principals.
     * @see org.jsecurity.context.SecurityContext#getPrincipal()
     */
    public Principal getPrincipal() {
        if( principals.isEmpty() ) {
            throw new IllegalStateException( "No principals are associated with this authorization context." );
        }
        return this.principals.get(0);
    }

    /**
     * @see org.jsecurity.context.SecurityContext#getAllPrincipals()
     */
    public List<Principal> getAllPrincipals() {
        return principals;
    }

    /**
     * @see org.jsecurity.context.SecurityContext#getPrincipalByType(Class) ()
     */
    public Principal getPrincipalByType(Class principalType) throws NoSuchPrincipalException {
        for( Principal principal : principals ) {
            if( principalType.isAssignableFrom( principal.getClass() ) ) {
                return principal;
            }
        }
        return null;
    }

    /**
     * @see org.jsecurity.context.SecurityContext#getAllPrincipalsByType(Class)()
     */
    public Collection<Principal> getAllPrincipalsByType(Class principalType) {
        Set<Principal> principalsOfType = new HashSet<Principal>();

        for( Principal principal : principals ) {
            if( principalType.isAssignableFrom( principal.getClass() ) ) {
                principalsOfType.add( principal );
            }
        }
        return principalsOfType;
    }

    public boolean hasRole( String roleIdentifier ) {
        return realmManager.hasRole( getPrincipal(), roleIdentifier );
    }

    public boolean[] hasRoles( List<String> roleIdentifiers ) {
        return realmManager.hasRoles( getPrincipal(), roleIdentifiers );
    }

    public boolean hasAllRoles( Collection<String> roleIdentifiers ) {
        return realmManager.hasAllRoles( getPrincipal(), roleIdentifiers );
    }

    public boolean implies( Permission permission ) {
        return realmManager.isPermitted( getPrincipal(), permission );
    }

    public boolean[] implies( List<Permission> permissions ) {
        return realmManager.isPermitted( getPrincipal(), permissions );
    }

    public boolean impliesAll( Collection<Permission> permissions ) {
        return realmManager.isPermittedAll( getPrincipal(), permissions );
    }

    public void checkPermission( Permission permission ) throws AuthorizationException {
        realmManager.checkPermission( getPrincipal(), permission );
    }

    public void checkPermissions( Collection<Permission> permissions )
        throws AuthorizationException {
        realmManager.checkPermissions( getPrincipal(), permissions );
    }

    public boolean isAuthenticated() {
        // The presence of this security context indicates that the user is authenticated
        return true;
    }

    public Session getSession() {
        return (Session) ThreadContext.get( ThreadContext.SESSION_KEY );
    }

    public void invalidate() {

        try {
            Session s = getSession();
            if ( s != null ) {
                s.stop();
            }
        } finally {
            ThreadContext.remove( ThreadContext.SESSION_KEY );
            ThreadContext.remove( ThreadContext.SECURITY_CONTEXT_KEY );
        }
    }

}