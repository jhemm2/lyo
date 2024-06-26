/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Eclipse Distribution License 1.0
 * which is available at http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */
package org.eclipse.lyo.server.oauth.webapp.services;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Checks requests to see if they have the right X-CSRF-Prevent header values. 
 * 
 * @author Samuel Padgett
 */
public class CSRFPrevent {
	private static final String CSRF_PREVENT_HEADER = "X-CSRF-Prevent";
    private static final Logger log = LoggerFactory.getLogger(CSRFPrevent.class);

    public static void check(HttpServletRequest httpRequest) {
		String csrfPrevent = httpRequest.getHeader(CSRF_PREVENT_HEADER);
		String sessionId = httpRequest.getSession().getId();
		if (!sessionId.equals(csrfPrevent)) {
		    log.warn("Request denied due to possible CSRF attack. Expected X-CSRF-Prevent header: {}. Received: {}", sessionId, csrfPrevent);
			throw new WebApplicationException(Response.status(Status.FORBIDDEN)
					.entity("Request denied due to possible CSRF attack.").type(MediaType.TEXT_PLAIN).build());
		}
	}
}
