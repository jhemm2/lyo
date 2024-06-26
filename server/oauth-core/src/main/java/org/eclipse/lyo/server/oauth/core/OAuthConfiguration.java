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
package org.eclipse.lyo.server.oauth.core;

import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.lyo.server.oauth.core.consumer.ConsumerStore;
import org.eclipse.lyo.server.oauth.core.consumer.ConsumerStoreException;
import org.eclipse.lyo.server.oauth.core.token.SimpleTokenStrategy;
import org.eclipse.lyo.server.oauth.core.token.TokenStrategy;

import net.oauth.OAuthProblemException;
import net.oauth.OAuthValidator;
import net.oauth.SimpleOAuthValidator;
import net.oauth.http.HttpMessage;

/**
 * Manages the OAuth provider configuration, including the validator, consumer store, and
 * token strategy.
 * 
 * @author Samuel Padgett
 */
public class OAuthConfiguration {
	private OAuthValidator validator;
	private TokenStrategy tokenStrategy;
	private ConsumerStore consumerStore = null;
	private Application application = null;
	private boolean v1_0Allowed = true;
	private String servletUri = null;

	private static final OAuthConfiguration instance = new OAuthConfiguration();

	public static OAuthConfiguration getInstance() {
		return instance;
	}

	private OAuthConfiguration() {
		validator = new SimpleOAuthValidator();
		tokenStrategy = new SimpleTokenStrategy();
	}

	/**
	 * Gets the OAuth validator for validating request signatures.
	 * 
	 * @return the validator
	 */
	public OAuthValidator getValidator() {
		return validator;
	}

	/**
	 * Sets the OAuth validator for validating request signatures.
	 * 
	 * @param validator the validator
	 */
	public void setValidator(OAuthValidator validator) {
		this.validator = validator;
	}

	/**
	 * Gets the strategy used to generate and verify OAuth tokens.
	 * 
	 * @return the token strategy
	 */
	public TokenStrategy getTokenStrategy() {
		return tokenStrategy;
	}

	/**
	 * Sets the strategy used to generate and verify OAuth tokens.
	 * 
	 * @param tokenStrategy the strategy
	 */
	public void setTokenStrategy(TokenStrategy tokenStrategy) {
		this.tokenStrategy = tokenStrategy;
	}

	/**
	 * Gets the store used for managing consumers.
	 * 
	 * @return the consumer store
	 */
	public ConsumerStore getConsumerStore() {
		return consumerStore;
	}

	/**
	 * Sets the store used for managing consumers.
	 * 
	 * @param consumerStore the consumer store
	 * @throws ConsumerStoreException on errors initializing the consumer registry
	 */
	public void setConsumerStore(ConsumerStore consumerStore) throws ConsumerStoreException {
		this.consumerStore = consumerStore;
	}

	public Application getApplication() throws OAuthProblemException {
		if (application == null) {
			OAuthProblemException e = new OAuthProblemException();
			e.setParameter(HttpMessage.STATUS_CODE,
					HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			throw e;
		}

		return application;
	}

	public void setApplication(Application application) {
		this.application = application;
	}

	/**
	 * Is OAuth version 1.0 allowed, or do we require 1.0a?
	 * 
	 * @return true if version 1.0 is allowed
	 * @see <a href="http://oauth.net/advisories/2009-1/">OAuth Security Advisory: 2009.1</a>
	 */
	public boolean isV1_0Allowed() {
		return v1_0Allowed;
	}

	/**
	 * Sets if we allow OAuth 1.0.
	 * 
	 * @param allowed
	 *            true to allow OAuth version 1.0 requests or false to require
	 *            OAuth version 1.0a
	 * @see <a href="http://oauth.net/advisories/2009-1/">OAuth Security Advisory: 2009.1</a>
	 */
	public void setV1_0Allowed(boolean allowed) {
		this.v1_0Allowed = allowed;
	}

    public String getServletUri() {
        return servletUri;
    }

    /**
     * Sets the official servlet URL (typically set using OSLC4JUtils.getServletURI()) 
     * in case this can differ from that in the individual requests.
     * This is important to set correctly to compute the digital signature.
     * An individual request URL is then constructed by appending this servletUrl with request.getPathInfo().
     * 
     * @param servletUri
     *            the official servlet URL of the request. If this
     *            parameter is null, this method will try to reconstruct the URL
     *            from the HTTP request; which may be wrong in some cases.
     */
    public void setServletUri(String servletUri) {
        this.servletUri = servletUri;
    }
}
