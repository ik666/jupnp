/*
 * Copyright (C) 2011-2025 4th Line GmbH, Switzerland and others
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License Version 1 or later
 * ("CDDL") (collectively, the "License"). You may not use this file
 * except in compliance with the License. See LICENSE.txt for more
 * information.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * SPDX-License-Identifier: CDDL-1.0
 */
package org.jupnp.xml;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Christian Bauer
 */
public class SAXParser {

    public static final URI XML_SCHEMA_NAMESPACE = URI.create("http://www.w3.org/2001/xml.xsd");
    public static final URL XML_SCHEMA_RESOURCE = Thread.currentThread().getContextClassLoader()
            .getResource("org/jupnp/schemas/xml.xsd");

    // Cache expensive factory instances for better performance
    private static final SAXParserFactory SAX_PARSER_FACTORY;
    private static final SchemaFactory SCHEMA_FACTORY;

    static {
        SAX_PARSER_FACTORY = SAXParserFactory.newInstance();
        SAX_PARSER_FACTORY.setNamespaceAware(true);
        SCHEMA_FACTORY = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        SCHEMA_FACTORY.setResourceResolver(new CatalogResourceResolver(new HashMap<>() {
            {
                put(XML_SCHEMA_NAMESPACE, XML_SCHEMA_RESOURCE);
            }
        }));
    }

    private final XMLReader xr;

    public SAXParser() {
        this(null);
    }

    public SAXParser(DefaultHandler handler) {
        this.xr = create();
        if (handler != null) {
            xr.setContentHandler(handler);
        }
    }

    public void setContentHandler(ContentHandler handler) {
        xr.setContentHandler(handler);
    }

    /**
     * Creates an XMLReader instance. If schema sources are available, creates a validating reader.
     * Uses cached factory instances for better performance.
     *
     * @return A configured XMLReader instance
     * @throws RuntimeException if XMLReader creation fails
     */
    protected XMLReader create() {
        try {
            final XMLReader xmlReader;
            Source[] schemaSources = getSchemaSources();
            // Use cached factory for better performance
            synchronized (SAX_PARSER_FACTORY) {
                if (schemaSources != null) {
                    // Jump through all the hoops and create a validating reader
                    SAX_PARSER_FACTORY.setSchema(createSchema(schemaSources));
                } else {
                    // Reset schema to null for non-validating parser
                    SAX_PARSER_FACTORY.setSchema(null);
                }
                xmlReader = SAX_PARSER_FACTORY.newSAXParser().getXMLReader();
            }
            xmlReader.setErrorHandler(getErrorHandler());
            return xmlReader;
        } catch (SAXException e) {
            throw new RuntimeException("Failed to create XMLReader", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create XMLReader", e);
        }
    }

    /**
     * Creates a Schema from the given sources. Uses a cached SchemaFactory for better performance.
     *
     * @param schemaSources The schema sources to compile
     * @return A compiled Schema instance
     * @throws RuntimeException if schema creation fails
     */
    protected Schema createSchema(Source[] schemaSources) {
        try {
            // Use cached schema factory for better performance
            synchronized (SCHEMA_FACTORY) {
                return SCHEMA_FACTORY.newSchema(schemaSources);
            }
        } catch (SAXException e) {
            throw new RuntimeException("Failed to create schema", e);
        }
    }

    protected Source[] getSchemaSources() {
        return null;
    }

    protected ErrorHandler getErrorHandler() {
        return new SimpleErrorHandler();
    }

    public void parse(InputSource source) throws ParserException {
        try {
            xr.parse(source);
        } catch (Exception e) {
            throw new ParserException(e);
        }
    }

    /**
     * Always throws exceptions and stops parsing.
     */
    public static class SimpleErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException e) throws SAXException {
            throw new SAXException(e);
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw new SAXException(e);
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw new SAXException(e);
        }
    }

    public static class Handler<I> extends DefaultHandler {
        private final Logger logger = LoggerFactory.getLogger(SAXParser.class);

        protected SAXParser parser;
        protected I instance;
        protected Handler parent;
        protected StringBuilder characters = new StringBuilder();
        protected Attributes attributes;

        public Handler(I instance) {
            this(instance, null, null);
        }

        public Handler(I instance, SAXParser parser) {
            this(instance, parser, null);
        }

        public Handler(I instance, Handler parent) {
            this(instance, parent.getParser(), parent);
        }

        public Handler(I instance, SAXParser parser, Handler parent) {
            this.instance = instance;
            this.parser = parser;
            this.parent = parent;
            if (parser != null) {
                parser.setContentHandler(this);
            }
        }

        public I getInstance() {
            return instance;
        }

        public SAXParser getParser() {
            return parser;
        }

        public Handler getParent() {
            return parent;
        }

        protected void switchToParent() {
            if (parser != null && parent != null) {
                parser.setContentHandler(parent);
                attributes = null;
            }
        }

        public String getCharacters() {
            return characters.toString();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            this.characters = new StringBuilder();
            this.attributes = new AttributesImpl(attributes); // see http://docstore.mik.ua/orelly/xml/sax2/ch05_01.htm,
                                                              // section 5.1.1
            logger.trace("{} starting: {}", getClass().getSimpleName(), localName);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            characters.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {

            if (isLastElement(uri, localName, qName)) {
                logger.trace("{}: last element, switching to parent: {}", getClass().getSimpleName(), localName);
                switchToParent();
                return;
            }

            logger.trace("{} ending: {}", getClass().getSimpleName(), localName);
        }

        protected boolean isLastElement(String uri, String localName, String qName) {
            return false;
        }

        protected Attributes getAttributes() {
            return attributes;
        }
    }
}
