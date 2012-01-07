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
package com.smartitengineering.emailq.service.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.smartitengineering.cms.repo.dao.impl.ExtendedReadDao;
import com.smartitengineering.dao.common.CommonDao;
import com.smartitengineering.dao.common.queryparam.MatchMode;
import com.smartitengineering.dao.common.queryparam.QueryParameter;
import com.smartitengineering.dao.common.queryparam.QueryParameterFactory;
import com.smartitengineering.emailq.domain.Email;
import com.smartitengineering.emailq.domain.Email.Attachments;
import com.smartitengineering.emailq.domain.Email.Message.MsgType;
import com.smartitengineering.emailq.service.EmailService;
import com.smartitengineering.emailq.service.Emails;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Semaphore;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import org.apache.commons.lang.StringUtils;
import org.quartz.DateIntervalTrigger;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author imyousuf
 */
@Singleton
public class EmailServiceImpl implements EmailService {

  @Inject
  private CommonDao<Email, String> commonDao;
  @Inject
  private ExtendedReadDao<Email, String> extendedReadDao;
  @Inject
  private Session session;
  @Inject(optional = true)
  @Named("mailSenderCronDelayInSeonds")
  private Integer period = new Integer(120);
  @Inject(optional = true)
  @Named("mailSenderCronEnabled")
  private Boolean cronEnabled = false;
  private Scheduler scheduler;
  private final Semaphore sendEmailMutex = new Semaphore(1);
  private final transient Logger logger = LoggerFactory.getLogger(getClass());
  private Transport transport;

  @Inject
  public void initSendMailCron() {
    if (!cronEnabled.booleanValue()) {
      return;
    }
    try {
      transport = session.getTransport("smtp");
    }
    catch (Exception ex) {
      logger.error("Could not initialize SMTP transport", ex);
      throw new IllegalStateException(ex);
    }
    try {
      scheduler = StdSchedulerFactory.getDefaultScheduler();
      JobDetail detail = new JobDetail("sendEmailJob", "sendEmailPoll", SendEmailJob.class);
      Trigger trigger = new DateIntervalTrigger("sendEmailTrigger", "sendEmailPoll",
                                                DateIntervalTrigger.IntervalUnit.SECOND, period.intValue());
      scheduler.setJobFactory(new JobFactory() {

        public Job newJob(TriggerFiredBundle bundle) throws SchedulerException {
          try {
            Class<? extends Job> jobClass = bundle.getJobDetail().getJobClass();
            if (EmailServiceImpl.class.equals(jobClass.getEnclosingClass())) {
              Constructor<? extends Job> constructor =
                                         (Constructor<? extends Job>) jobClass.getDeclaredConstructors()[0];
              constructor.setAccessible(true);
              Job job = constructor.newInstance(EmailServiceImpl.this);
              return job;
            }
            else {
              return jobClass.newInstance();
            }
          }
          catch (Exception ex) {
            throw new SchedulerException(ex);
          }
        }
      });
      scheduler.start();
      scheduler.scheduleJob(detail, trigger);
    }
    catch (Exception ex) {
      logger.error("Could not start cron job!", ex);
      throw new IllegalStateException(ex);
    }
  }

  private class SendEmailJob implements Job {

    public void execute(JobExecutionContext context) throws JobExecutionException {
      try {
        sendEmailMutex.acquire();
      }
      catch (Exception ex) {
        logger.warn("Could not acquire lock!", ex);
        throw new JobExecutionException(ex);
      }
      try {
        sendPendingEmails();
      }
      catch (Exception ex) {
        logger.error("Error sending pending emails", ex);
        throw new JobExecutionException(ex);
      }
      finally {
        sendEmailMutex.release();
      }
    }
  }

  protected void sendPendingEmails() throws Exception {
    QueryParameter statusParam = QueryParameterFactory.getStringLikePropertyParam(Email.PROPERTY_MAILSTATUS,
                                                                                  Email.MailStatus.NOT_SENT.name(),
                                                                                  MatchMode.EXACT);
    long count = extendedReadDao.count(statusParam);
    if (count > 0) {
      Collection<Email> emails = commonDao.getList(statusParam, QueryParameterFactory.getMaxResultsParam((int) count),
                                                   QueryParameterFactory.getFirstResultParam(0));
      if (emails != null && !emails.isEmpty()) {
        if (logger.isInfoEnabled()) {
          logger.info(new StringBuilder("Number of messages attempting to send ").append(emails.size()).toString());
        }
        List<Email> successfulEmails = new ArrayList<Email>();
        logger.debug("Connecting to SMTP server");
        transport.connect();
        try {
          for (Email email : emails) {
            sendEmail(email, successfulEmails);
          }
        }
        finally {
          logger.debug("Closing tunnel with SMTP server");
          transport.close();
        }
        if (!successfulEmails.isEmpty()) {
          if (logger.isInfoEnabled()) {
            logger.info(new StringBuilder("Number of messages sent ").append(successfulEmails.size()).toString());
          }
          commonDao.update(successfulEmails.toArray(new Email[successfulEmails.size()]));
        }
      }
    }
  }

  protected void sendEmail(Email email,
                           List<Email> successfulEmails) {
    try {
      if (logger.isDebugEnabled()) {
        logger.debug("Attempting to send " + email.getId() + " " + email.getSubject());
        if (email.getTo() != null) {
          logger.debug("To: " + Arrays.toString(email.getTo().toArray()));
        }
        else {
          logger.debug("To is NULL");
        }
        if (email.getTo() != null) {
          logger.debug("CC: " + Arrays.toString(email.getCc().toArray()));
        }
        else {
          logger.debug("CC is NULL");
        }
        if (email.getTo() != null) {
          logger.debug("BCC: " + Arrays.toString(email.getBcc().toArray()));
        }
        else {
          logger.debug("BCC is NULL");
        }
        if (email.getTo() != null) {
          logger.debug("FROM: " + email.getFrom());
        }
        else {
          logger.debug("FROM is NULL");
        }
        if (email.getAttachments() != null) {
          logger.debug("Attachments: " + Arrays.toString(email.getAttachments().toArray()));
          for (Attachments attachment : email.getAttachments()) {
            logger.debug("Attachment: " + attachment.getName());
          }
        }
        else {
          logger.debug("No attachments");
        }
      }
      //Send email
      if (StringUtils.isBlank(email.getSubject()) || StringUtils.isBlank(email.getFrom())) {
        logger.warn(new StringBuilder("Invalid email without either from or a subject, thus ignoring it ").append(email.
            getId()).toString());
        return;
      }
      MimeMessage message = new MimeMessage(session);
      message.setSubject(email.getSubject());
      message.setFrom(new InternetAddress(email.getFrom()));
      addRecipients(message, Message.RecipientType.TO, email.getTo());
      addRecipients(message, Message.RecipientType.CC, email.getCc());
      addRecipients(message, Message.RecipientType.BCC, email.getBcc());
      if (email.getMessage() != null && StringUtils.isNotBlank(email.getMessage().getMsgBody()) && email.getMessage().
          getMsgType().equals(MsgType.PLAIN) && (email.getAttachments() == null || email.getAttachments().isEmpty())) {
        message.setText(email.getMessage().getMsgBody());
      }
      else {
        Multipart multipart = new MimeMultipart();
        if (email.getMessage() != null && StringUtils.isNotBlank(email.getMessage().getMsgBody())) {
          MimeBodyPart bodyPart = new MimeBodyPart();
          switch (email.getMessage().getMsgType()) {
            case HTML:
              bodyPart.setContent(email.getMessage().getMsgBody(), "html");
              break;
            case PLAIN:
            default:
              bodyPart.setText(email.getMessage().getMsgBody());
          }
          multipart.addBodyPart(bodyPart);
        }
        if (email.getAttachments() != null && !email.getAttachments().isEmpty()) {
          for (Attachments attachment : email.getAttachments()) {
            addAttachment(multipart, attachment);
          }
        }
        message.setContent(multipart);
      }
      Transport.send(message);
      if (logger.isDebugEnabled()) {
        logger.debug("Sent " + email.getId());
      }
      //Update status
      email.setMailStatus(Email.MailStatus.SENT);
      successfulEmails.add(email);
      if (logger.isDebugEnabled()) {
        logger.debug("Set new mail status and add to successful queue " + email.getSubject());
      }
    }
    catch (Exception ex) {
      logger.warn(new StringBuilder("Error sending email with subject ").append(email.getSubject()).toString(),
                  ex);
    }
  }

  protected void addRecipients(MimeMessage message, RecipientType recipientType, Collection<String> addresses) throws
      MessagingException {
    if (addresses != null && !addresses.isEmpty()) {
      Address[] addressArray = new Address[addresses.size()];
      int index = 0;
      for (String address : addresses) {
        addressArray[index++] = new InternetAddress(address);
      }
      message.addRecipients(recipientType, addressArray);
    }
  }

  private void addAttachment(Multipart multipart, Attachments attachment) throws MessagingException {
    MimeBodyPart attachmentPart = new MimeBodyPart();
    attachmentPart.setFileName(attachment.getName());
    if (StringUtils.isNotBlank(attachment.getDescription())) {
      attachmentPart.setDescription(attachment.getDescription());
    }
    if (StringUtils.isNotBlank(attachment.getDisposition())) {
      attachmentPart.setDisposition(attachment.getDisposition());
    }
    DataSource source = new ByteArrayDataSource(attachment.getBlob(), attachment.getContentType());
    attachmentPart.setDataHandler(new DataHandler(source));
    multipart.addBodyPart(attachmentPart);
  }

  public boolean saveEmail(com.smartitengineering.emailq.domain.Email email) {
    // No deliverable configured
    if ((email.getTo() == null || email.getTo().isEmpty()) && (email.getCc() == null || email.getCc().isEmpty()) &&
        (email.getBcc() == null || email.getBcc().isEmpty())) {
      logger.warn("Ignoring email as no deliverable address is set!");
      return false;
    }
    // No from configured
    if (StringUtils.isBlank(email.getFrom())) {
      logger.warn("No From configured!");
      return false;
    }
    // No message body
    if (email.getMessage() == null || email.getMessage().getMsgType() == null || StringUtils.isBlank(email.getMessage().
        getMsgBody())) {
      logger.warn("No message body set!");
      return false;
    }
    try {
      email.setMailStatus(Email.MailStatus.NOT_SENT);
      commonDao.save(email);
      return true;
    }
    catch (Exception ex) {
      logger.warn("Could not save email", ex);
      return false;
    }
  }

  public Emails getEmails(com.smartitengineering.dao.common.queryparam.QueryParameter... params) {
    long count = extendedReadDao.count(params);
    Collection<Email> emailCollcn = commonDao.getList(params);
    Emails emails = new Emails();
    emails.setTotalCount(count);
    emails.setEmails(emailCollcn);
    return emails;
  }
}
