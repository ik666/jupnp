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
package org.jupnp.model.message.header;

/**
 * Thrown if the value of a header was invalid for the given header.
 * 
 * @author Christian Bauer
 */
public class InvalidHeaderException extends RuntimeException {

    /**
     * Stackless on purpose: {@link UpnpHeader#newInstance} tries every candidate header class and uses
     * this exception to reject the ones that do not parse, so it is thrown for nearly every header of
     * every incoming message - tens of thousands per hour on a quiet network. Only the message is ever
     * read, never the stack trace, and filling one in dominated the cost of parsing.
     */
    public InvalidHeaderException(String message) {
        super(message, null, false, false);
    }

    public InvalidHeaderException(String message, Throwable cause) {
        super(message, cause, false, false);
    }
}
