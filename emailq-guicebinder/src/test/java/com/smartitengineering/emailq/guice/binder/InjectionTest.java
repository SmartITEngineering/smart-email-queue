/*
 *
 * This is a simple Content Management System (CMS)
 * Copyright (C) 2010  Imran M Yousuf (imyousuf@smartitengineering.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.smartitengineering.emailq.guice.binder;

import com.smartitengineering.cms.api.common.MediaType;
import com.smartitengineering.emailq.binder.guice.Initializer;
import com.smartitengineering.emailq.service.Services;
import com.smartitengineering.util.rest.client.ApplicationWideClientFactoryImpl;
import com.smartitengineering.util.rest.client.jersey.cache.CacheableClient;
import com.sun.jersey.api.client.Client;
import javax.ws.rs.core.HttpHeaders;
import junit.framework.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class InjectionTest {

  @BeforeClass
  public static void setUp() throws Exception {
    Initializer.init();
    /*
     * Setup client properties
     */
    System.setProperty(ApplicationWideClientFactoryImpl.TRACE, "true");

    Client client = CacheableClient.create();
    client.resource("http://localhost:10080/hub/api/channels/test").header(HttpHeaders.CONTENT_TYPE,
                                                                           MediaType.APPLICATION_JSON).put(
        "{\"name\":\"test\"}");
  }

  @Test
  public void testApi() {
    Assert.assertNotNull(Services.getInstance());
    Assert.assertNotNull(Services.getInstance().getEmailService());
  }
}
