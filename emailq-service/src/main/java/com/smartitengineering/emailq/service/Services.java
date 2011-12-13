/*
 *
 * This is a simple Email Queue management system
 * Copyright (C) 2011  Imran M Yousuf (imyousuf@smartitengineering.com)
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
package com.smartitengineering.emailq.service;

import com.smartitengineering.util.bean.BeanFactoryRegistrar;
import com.smartitengineering.util.bean.annotations.Aggregator;
import com.smartitengineering.util.bean.annotations.InjectableField;
import java.util.concurrent.Semaphore;

/**
 *
 * @author imyousuf
 */
@Aggregator(contextName = Services.CONTEXT_NAME)
public class Services {

  public static final String CONTEXT_NAME = "com.smartitengineering.email.queue.service";
  private static final Semaphore semaphore = new Semaphore(1);
  @InjectableField
  private EmailService emailService;

  private Services() {
  }

  public EmailService getEmailService() {
    return emailService;
  }
  private static Services services;

  public static Services getInstance() {
    if (services == null) {
      try {
        semaphore.acquire();
      }
      catch (Exception ex) {
        throw new IllegalStateException(ex);
      }
      try {
        initServices();
      }
      finally {
        semaphore.release();
      }
    }
    return services;
  }

  private synchronized static void initServices() {
    if (services == null) {
      services = new Services();
      BeanFactoryRegistrar.aggregate(services);
    }
  }
}
