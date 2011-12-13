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

import com.smartitengineering.emailq.domain.Email;
import java.util.Collection;

/**
 *
 * @author imyousuf
 */
public class Emails {

  private int totalCount;
  private Collection<Email> emails;

  public Collection<Email> getEmails() {
    return emails;
  }

  public void setEmails(Collection<Email> emails) {
    this.emails = emails;
  }

  public int getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(int totalCount) {
    this.totalCount = totalCount;
  }
}
