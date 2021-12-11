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
package org.eclipse.lyo.oslc4j.core.servlet;

import java.net.URI;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.eclipse.lyo.core.utils.marshallers.LyoConfigUtil;
import org.eclipse.lyo.oslc4j.core.OSLC4JUtils;
import org.eclipse.lyo.oslc4j.core.model.ServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServletListener
	   implements ServletContextListener
{
    private static final Logger log = LoggerFactory.getLogger(ServletListener.class);

    public static final String DEFAULT_SCHEME = "http";
    public static final String DEFAULT_HOST = LyoConfigUtil.getHost();
    public static final String DEFAULT_PORT = "8080";

    private String serviceProviderIdentifier;

	public ServletListener()
	{
		super();
	}

	@Override
	public void contextDestroyed(final ServletContextEvent servletContextEvent)
	{
		if (serviceProviderIdentifier != null)
		{
			try
			{
				ServiceProviderCatalogSingleton.deregisterServiceProvider(serviceProviderIdentifier);
			}
			catch (final Exception exception)
			{
				log.error("Unable to deregister with service provider catalog", exception);
			}
			finally
			{
				serviceProviderIdentifier = null;
			}
		}
	}

	@Override
	public void contextInitialized(final ServletContextEvent servletContextEvent)
	{
		try
		{
			final ServletContext servletContext = servletContextEvent.getServletContext();

            log.info("Initializing OSLC4J Registry");

			//Honor the public URI property, if set
			String publicURI = OSLC4JUtils.getPublicURI();
			String baseURI;

            if (publicURI == null || publicURI.isEmpty()) {
                String scheme = LyoConfigUtil.getOslcConfigProperty("scheme", DEFAULT_SCHEME, servletContext, ServletListener.class);
                String host = LyoConfigUtil.getOslcConfigProperty("host", DEFAULT_HOST, servletContext, ServletListener.class);
                String port = LyoConfigUtil.getOslcConfigProperty("port", DEFAULT_PORT, servletContext, ServletListener.class);

                baseURI = scheme + "://" + host + ":" + port + servletContext.getContextPath();
            } else {
                //Instead of the context in the publicUri, need to use context for the registry
                URI uri = new URI(publicURI);
                baseURI = uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + servletContext.getContextPath();
            }

			final ServiceProvider serviceProvider = ServiceProviderFactory.createServiceProvider(baseURI);

			final ServiceProvider registeredServiceProvider = ServiceProviderCatalogSingleton.registerServiceProvider(baseURI,
																													  serviceProvider);

			serviceProviderIdentifier = registeredServiceProvider.getIdentifier();
            log.info("Service Provider registered at '{}'", registeredServiceProvider.getAbout());
            log.info("OSLC4J Registry started at '{}'", baseURI);
		}
		catch (final Exception exception)
		{
			log.error("Unable to register with service provider catalog", exception);
		}
	}


}
