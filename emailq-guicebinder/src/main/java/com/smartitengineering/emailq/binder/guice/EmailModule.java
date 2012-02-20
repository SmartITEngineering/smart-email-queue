/*
 *
 * This is a simple Email Queue management system
 * Copyright (C) 2012  Imran M Yousuf (imyousuf@smartitengineering.com)
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
package com.smartitengineering.emailq.binder.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.smartitengineering.emailq.service.EmailService;
import com.smartitengineering.emailq.service.impl.EmailServiceImpl;
import com.smartitengineering.util.bean.PropertiesLocator;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import org.apache.commons.lang.math.NumberUtils;

/**
 *
 * @author imyousuf
 */
public class EmailModule extends AbstractModule {

  public static final String DOMAIN_PROPS = "domainProps";
  public static final String CRON_JOB_DELAY = "cronJobDelayInSeconds";
  public static final String CRON_ENABLED = "cronEnabled";
  public static final String SMTP_HOST = "mail.smtp.host";
  public static final String SMTP_PORT = "mail.smtp.port";
  public static final String SMTP_USER = "mail.smtp.user";
  public static final String SMTP_PWD = "mail.smtp.password";
  public static final String SMTP_AUTH = "mail.smtp.auth";
  public static final String SMTP_TLS = "mail.smtp.tls";
  public static final String SMTP_SSL = "mail.smtp.ssl";
  private static final String JAVAMAIL_SMTP_HOST = SMTP_HOST;
  private static final String JAVAMAIL_SMTP_PORT = SMTP_PORT;
  private static final String JAVAMAIL_SMTP_USER = SMTP_USER;
  private static final String JAVAMAIL_SMTP_AUTH = SMTP_AUTH;
  private static final String JAVAMAIL_SMTP_TLS = "mail.smtp.starttls.enabled";
  private static final String JAVAMAIL_SMTP_SSL_SOCKET_FACTORY_CLASS = "mail.smtp.socketFactory.class";
  private static final String JAVAMAIL_SMTP_SSL_SOCKET_FACTORY_CLASS_VAL = "javax.net.ssl.SSLSocketFactory";
  private static final String JAVAMAIL_SMTP_SSL_SOCKET_FACTORY_PORT = "mail.smtp.socketFactory.port";
  private final String workspaceIdNamespace;
  private final String workspaceIdName;
  private final String reportNamespace;
  private final String smtpHost, smtpUser, smtpPassword;
  private final int smtpPort, cronDelayInSeconds;
  private final boolean authEnabled, tlsEnabled, sslEnabled, cronEnabled;

  public EmailModule(Properties properties) {
    if (properties == null) {
      workspaceIdNamespace = "";
      workspaceIdName = "";
      reportNamespace = "";
      smtpHost = "localhost";
      smtpUser = "";
      smtpPassword = "";
      smtpPort = 2525;
      authEnabled = false;
      tlsEnabled = false;
      sslEnabled = false;
      cronDelayInSeconds = 120;
      cronEnabled = true;
    }
    else {
      smtpHost = properties.getProperty(SMTP_HOST, "localhost");
      smtpUser = properties.getProperty(SMTP_USER, "");
      smtpPassword = properties.getProperty(SMTP_PWD, "");
      smtpPort = NumberUtils.toInt(properties.getProperty(SMTP_PORT), 2525);
      authEnabled = Boolean.parseBoolean(properties.getProperty(SMTP_AUTH));
      tlsEnabled = Boolean.parseBoolean(properties.getProperty(SMTP_TLS));
      sslEnabled = Boolean.parseBoolean(properties.getProperty(SMTP_SSL));
      cronEnabled = Boolean.parseBoolean(properties.getProperty(CRON_ENABLED));
      cronDelayInSeconds = NumberUtils.toInt(properties.getProperty(CRON_JOB_DELAY), -1);
      PropertiesLocator propertiesLocator = new PropertiesLocator();
      propertiesLocator.setSmartLocations(properties.getProperty(DOMAIN_PROPS));
      Properties mainProps = new Properties();
      try {
        propertiesLocator.loadProperties(mainProps);
      }
      catch (Exception ex) {
        throw new IllegalStateException(ex);
      }
      workspaceIdNamespace = mainProps.getProperty(
          "com.smartitengineering.emailq.domains.workspaceId.namespace", "");
      workspaceIdName = mainProps.getProperty("com.smartitengineering.emailq.domains.workspaceId.name", "");
      reportNamespace = mainProps.getProperty(
          "com.smartitengineering.emailq.domains.namespace", "");
    }
  }

  @Override
  protected void configure() {
    bind(EmailService.class).to(EmailServiceImpl.class).asEagerSingleton();
    configureJavaMailSession();
    if (cronDelayInSeconds > 0) {
      bind(Integer.class).annotatedWith(Names.named("mailSenderCronDelayInSeonds")).toInstance(new Integer(
          cronDelayInSeconds));
    }
    bind(Boolean.class).annotatedWith(Names.named("mailSenderCronEnabled")).toInstance(cronEnabled);
  }

  private void configureJavaMailSession() {
    Properties properties = new Properties();
    properties.setProperty(JAVAMAIL_SMTP_HOST, smtpHost);
    properties.setProperty(JAVAMAIL_SMTP_PORT, String.valueOf(smtpPort));
    if (sslEnabled) {
      properties.setProperty(JAVAMAIL_SMTP_SSL_SOCKET_FACTORY_CLASS, JAVAMAIL_SMTP_SSL_SOCKET_FACTORY_CLASS_VAL);
      properties.setProperty(JAVAMAIL_SMTP_SSL_SOCKET_FACTORY_PORT, String.valueOf(smtpPort));
    }
    else if (tlsEnabled) {
      properties.setProperty(JAVAMAIL_SMTP_TLS, String.valueOf(tlsEnabled));
    }
    if (authEnabled) {
      properties.setProperty(JAVAMAIL_SMTP_USER, smtpUser);
      properties.setProperty(JAVAMAIL_SMTP_AUTH, String.valueOf(authEnabled));
      bind(Session.class).toInstance(Session.getInstance(properties, new Authenticator() {

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
          return new PasswordAuthentication(smtpUser, smtpPassword);
        }
      }));
    }
    else {
      bind(Session.class).toInstance(Session.getInstance(properties));
    }
  }
}
