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
package org.eclipse.lyo.oslc4j.provider.jena;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import org.eclipse.lyo.oslc4j.core.model.OslcMediaType;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;

@Provider
@Produces({OslcMediaType.APPLICATION_RDF_XML})
@Consumes({OslcMediaType.APPLICATION_RDF_XML})
public class OslcSimpleRdfXmlArrayProvider
	   extends OslcRdfXmlArrayProvider
{
	public OslcSimpleRdfXmlArrayProvider()
	{
		super();
	}

	@Override
	public void writeTo(final Object[]						 objects,
						final Class<?>						 type,
						final Type							 genericType,
						final Annotation[]					 annotations,
						final MediaType						 mediaType,
						final MultivaluedMap<String, Object> map,
						final OutputStream					 outputStream)
		   throws IOException,
				  WebApplicationException
	{
		writeTo(objects,
				mediaType,
				map,
				outputStream,
				null,
				null,
				null,
				null);
	}
}