package com.smartitengineering.emailq.app.sender;

import com.smartitengineering.emailq.domain.Email;
import com.smartitengineering.emailq.domain.Email.Attachments;
import com.smartitengineering.emailq.domain.Email.Message;
import com.smartitengineering.emailq.service.Services;
import com.smartitengineering.util.rest.client.jersey.cache.CacheableClient;
import com.sun.jersey.api.client.Client;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import javax.mail.internet.ContentDisposition;
import javax.mail.internet.ParseException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.server.SMTPServer;

public class AppNonSSLTest {

  protected final transient Logger logger = LoggerFactory.getLogger(getClass());
  private static SMTPServer smtpServer;

  @BeforeClass
  public static void start() {
    smtpServer = new SMTPServer(new CustomMessageHandlerFactory());
    smtpServer.setPort(2525);
    smtpServer.start();
    Client client = CacheableClient.create();
    client.resource("http://localhost:10080/hub/api/channels/test").header(HttpHeaders.CONTENT_TYPE,
                                                                           MediaType.APPLICATION_JSON).put(
        "{\"name\":\"test\"}");
    new SenderApp().init(false);
  }

  public static class CustomMessageHandlerFactory implements MessageHandlerFactory {

    public MessageHandler create(MessageContext mc) {
      return new Handler(mc);
    }
  }

  static class Handler implements MessageHandler {

    MessageContext ctx;
    protected final transient Logger logger = LoggerFactory.getLogger(getClass());

    public Handler(MessageContext ctx) {
      this.ctx = ctx;
    }

    public void from(String from) throws RejectException {
      logger.info("FROM:" + from);
    }

    public void recipient(String recipient) throws RejectException {
      logger.info("RECIPIENT:" + recipient);
    }

    public void data(InputStream data) throws IOException {
      StringBuilder dataStr = new StringBuilder();
      dataStr.append("MAIL DATA\n");
      dataStr.append("= = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =\n");
      dataStr.append(this.convertStreamToString(data));
      dataStr.append("\n= = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =\n");
      logger.info(dataStr.toString());
    }

    public void done() {
      logger.info("Finished");
    }

    public String convertStreamToString(InputStream is) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      StringBuilder sb = new StringBuilder();

      String line = null;
      try {
        while ((line = reader.readLine()) != null) {
          sb.append(line).append('\n');
        }
      }
      catch (IOException e) {
        logger.warn(e.getMessage(), e);
      }
      return sb.toString();
    }
  }

  @AfterClass
  public static void stop() {
    smtpServer.stop();
  }

  @Test
  public void testApp() {
    Assert.assertNotNull(Services.getInstance().getEmailService());
  }

  @Test
  public void testSavingEmail() throws InterruptedException, UnsupportedEncodingException, ParseException {
    Email email = new Email();
    email.setFrom("imran@smartitengineering.com");
    email.setTo(Arrays.<String>asList("imyousuf@gmail.com"));
    email.setCc(Arrays.<String>asList("jersey@smartitengineering.com"));
    email.setBcc(Arrays.<String>asList("imran.yousuf@smartitengineering.com"));
    email.setSubject("Test Subject");
    Message message = new Message();
    message.setMsgType(Message.MsgType.PLAIN);
    message.setMsgBody("Test Message Body");
    email.setMessage(message);
    Attachments attachment1 = new Attachments();
    attachment1.setContentType("text/plain");
    attachment1.setDescription("A sample properties file");
    ContentDisposition dispos = new ContentDisposition(
        "attachment;modification-date=\"Wed, 12 February 1997 16:29:51 -0500\"");
    attachment1.setDisposition(dispos.toString());
    attachment1.setName("att.txt");
    attachment1.setBlob("A plain text file".getBytes("UTF-8"));
    Attachments attachment2 = new Attachments();
    attachment2.setContentType("text/plain");
    attachment2.setName("att1.txt");
    attachment2.setBlob("Another plain text file".getBytes("UTF-8"));
    email.setAttachments(Arrays.asList(attachment1, attachment2));
    Assert.assertNotNull(email.getAttachments());
    Assert.assertFalse(email.getAttachments().isEmpty());
    Services.getInstance().getEmailService().saveEmail(email);
    Assert.assertNotNull(email.getAttachments());
    Assert.assertFalse(email.getAttachments().isEmpty());
    Thread.sleep(3000);
  }
}
