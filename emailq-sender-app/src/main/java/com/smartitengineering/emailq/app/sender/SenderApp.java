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
package com.smartitengineering.emailq.app.sender;

import com.smartitengineering.emailq.binder.guice.Initializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SenderApp {

  protected final transient Logger logger = LoggerFactory.getLogger(getClass());

  public SenderApp() {
  }

  protected void init(boolean waitThisThread) {
    Initializer.init();
    if (waitThisThread) {
      synchronized (this) {
        try {
          wait();
        }
        catch (InterruptedException ex) {
          logger.error(ex.getMessage(), ex);
        }
      }
    }
  }

  public void init() {
    init(true);
  }

  public static void main(String[] args) {
    SenderApp app = new SenderApp();
    app.init();
  }
}
