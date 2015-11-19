/*
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License, version 2 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/gpl-2.0.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 *
 * Copyright 2006 - 2015 Pentaho Corporation.  All rights reserved.
 */

package org.pentaho.platform.repository2.unified.jcr.jackrabbit.security;

import org.apache.jackrabbit.core.config.LoginModuleConfig;
import org.apache.jackrabbit.core.security.AnonymousPrincipal;
import org.apache.jackrabbit.core.security.principal.AdminPrincipal;
import org.apache.jackrabbit.core.security.principal.EveryonePrincipal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pentaho.platform.api.mt.ITenantedPrincipleNameResolver;
import org.pentaho.platform.core.mt.Tenant;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.engine.core.system.SimpleSystemSettings;
import org.pentaho.platform.repository2.unified.jcr.JcrAclMetadataStrategy;
import org.pentaho.test.platform.engine.core.MicroPlatform;
import org.springframework.security.userdetails.UserDetails;

import java.security.Principal;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

/**
 * @author Andrey Khayrutdinov
 */
public class SpringSecurityPrincipalProvider_PrincipalCreation_Test {

  private static final String ADMIN_ID = "notDefaultAdmin";
  private static final String ANONYMOUS_ID = "notDefaultAnonymous";

  private static final String USERNAME = "username";

  private SpringSecurityPrincipalProvider provider;
  private ITenantedPrincipleNameResolver userResolver;
  private MicroPlatform mp;

  @Before
  public void setUp() throws Exception {
    userResolver = mock( ITenantedPrincipleNameResolver.class );

    mp = new MicroPlatform();
    mp.defineInstance( "tenantedUserNameUtils", userResolver );
    mp.start();

    Properties properties = new Properties();
    properties.put( LoginModuleConfig.PARAM_ADMIN_ID, ADMIN_ID );
    properties.put( LoginModuleConfig.PARAM_ANONYMOUS_ID, ANONYMOUS_ID );
    provider = new SpringSecurityPrincipalProvider();
    provider.init( properties );
    // remove caching to avoid its impact
    provider.setCacheManager( null );
  }

  @After
  public void tearDown() throws Exception {
    mp.stop();
    provider = null;
    userResolver = null;
    mp = null;
  }


  @Test
  public void getPrincipal_Admin() throws Exception {
    Principal principal = provider.getPrincipal( ADMIN_ID );
    assertThat( principal, is( instanceOf( AdminPrincipal.class ) ) );
    assertEquals( ADMIN_ID, principal.getName() );
  }

  @Test
  public void getPrincipal_Anonymous() throws Exception {
    Principal principal = provider.getPrincipal( ANONYMOUS_ID );
    assertThat( principal, is( instanceOf( AnonymousPrincipal.class ) ) );
  }

  @Test
  public void getPrincipal_Everyone() throws Exception {
    Principal principal = provider.getPrincipal( EveryonePrincipal.getInstance().getName() );
    assertEquals( principal, EveryonePrincipal.getInstance() );
  }

  @Test
  public void getPrincipal_AclMetadataPrincipal() throws Exception {
    Principal principal =
      provider.getPrincipal( JcrAclMetadataStrategy.AclMetadataPrincipal.PRINCIPAL_PREFIX
        + JcrAclMetadataStrategy.AclMetadataPrincipal.SEPARATOR + USERNAME
        + JcrAclMetadataStrategy.AclMetadataPrincipal.SEPARATOR + USERNAME );
    assertThat( principal, is( instanceOf( JcrAclMetadataStrategy.AclMetadataPrincipal.class ) ) );
  }


  @Test
  public void getPrincipal_User_AccessesUserDetailsServiceByDefault() throws Exception {
    Principal principal = callGetPrincipalForUser( null, mock( UserDetails.class ) );
    assertEquals( USERNAME, principal.getName() );
    verify( provider, times( 1 ) ).internalGetUserDetails( USERNAME );
  }

  @Test
  public void getPrincipal_User_SkipsAccessingUserDetailsServiceAccordingToProperty() throws Exception {
    Principal principal = callGetPrincipalForUser( Boolean.FALSE, mock( UserDetails.class ) );
    assertEquals( USERNAME, principal.getName() );
    verify( provider, never() ).internalGetUserDetails( USERNAME );
  }

  @Test
  public void getPrincipal_User_ReturnsNullIfUserDetailsAreAbsent() throws Exception {
    Principal principal = callGetPrincipalForUser( Boolean.TRUE, null );
    assertNull( principal );
    verify( provider, times( 1 ) ).internalGetUserDetails( USERNAME );
  }

  private Principal callGetPrincipalForUser( Boolean verifyUser, UserDetails dummyDetails ) throws Exception {
    when( userResolver.isValid( USERNAME ) ).thenReturn( true );
    when( userResolver.getTenant( USERNAME ) ).thenReturn( new Tenant( USERNAME, true ) );

    SimpleSystemSettings settings = new SimpleSystemSettings();
    if ( verifyUser != null ) {
      settings.addSetting( "verify-user-on-principal-creation", verifyUser.toString() );
    }
    PentahoSystem.setSystemSettingsService( settings );

    provider = spy( provider );
    doReturn( dummyDetails ).when( provider ).internalGetUserDetails( USERNAME );

    return provider.getPrincipal( USERNAME );
  }
}
