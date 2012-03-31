/*
 * Copyright (c) 2010-2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.upcasting;

import org.axonframework.serializer.ChainingConverterFactory;
import org.axonframework.serializer.ContentTypeConverter;
import org.axonframework.serializer.ConverterFactory;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Represents a series of upcasters which are combined to upcast a {@link org.axonframework.serializer.SerializedObject}
 * to the most recent revision
 * of that payload. The intermediate representation required by each of the upcasters is converted using converters
 * provided by a converterFactory.
 * <p/>
 * Upcasters for different object types may be merged into a single chain, as long as the order of related upcasters
 * can be guaranteed.
 *
 * @author Allard Buijze
 * @author Frank Versnel
 * @since 2.0
 */
public class UpcasterChain {

    private final List<Upcaster> upcasters;
    private final ConverterFactory converterFactory;

    /**
     * Represents an empty UpcasterChain.
     */
    public static final UpcasterChain EMPTY = new UpcasterChain(Collections.<Upcaster>emptyList());

    /**
     * Initializes the UpcasterChain with a ChainingConverterFactory
     *
     * @param upcasters The upcasters forming the chain (in given order)
     */
    public UpcasterChain(List<Upcaster> upcasters) {
        this(new ChainingConverterFactory(), upcasters);
    }

    /**
     * Initialize a chain of the given <code>upcasters</code> and using the given <code>converterFactory</code> to
     * create converters for the intermediate representations used by the upcasters.
     *
     * @param converterFactory The factory providing ContentTypeConverter instances
     * @param upcasters        The upcasters forming the chain (in given order)
     */
    public UpcasterChain(ConverterFactory converterFactory, List<Upcaster> upcasters) {
        this.converterFactory = converterFactory;
        this.upcasters = new ArrayList<Upcaster>(upcasters);
    }

    /**
     * Initialize a chain of the given <code>upcasters</code> and using the given <code>converterFactory</code> to
     * create converters for the intermediate representations used by the upcasters.
     *
     * @param converterFactory The factory providing ContentTypeConverter instances
     * @param upcasters        The upcasters forming the chain (in given order)
     */
    public UpcasterChain(ConverterFactory converterFactory, Upcaster... upcasters) {
        this(converterFactory, Arrays.asList(upcasters));
    }

    /**
     * Pass the given <code>serializedObject</code> through the chain of upcasters. The result is a list of zero or
     * more serializedObjects representing the latest revision of the payload object.
     *
     * @param serializedObject the serialized object to upcast
     * @return the upcast SerializedObjects
     */
    public List<SerializedObject> upcast(SerializedObject serializedObject) {
        return upcast(new LinkedList<Upcaster>(upcasters), serializedObject);
    }

    private List<SerializedObject> upcast(Queue<Upcaster> upcasterChain, SerializedObject current) {
        // No upcasters are left in the queue, we're done.
        if (upcasterChain.isEmpty()) {
            return Collections.singletonList(current);
        }
        // There are still upcasters left to process.
        else {
            // Upcast the current IntermediateRepresentation.
            Upcaster upcaster = upcasterChain.poll();
            List<SerializedObject> unprocessedIntermediates = upcast(upcaster, current);

            // Process all intermediates returned by the top upcaster exhaustively
            List<SerializedObject> processedIntermediates = new ArrayList<SerializedObject>();
            for (SerializedObject unprocessedIntermediate : unprocessedIntermediates) {
                processedIntermediates.addAll(upcast(new LinkedList<Upcaster>(upcasterChain), unprocessedIntermediate));
            }

            return processedIntermediates;
        }
    }

    /**
     * Upcasts the given IntermediateRepresentation if the upcasters supports upcasting its type, otherwise returns
     * a list with the unprocessed IntermediateRepresentation as its only element.
     *
     * @param upcaster the upcaster that should do the upcasting
     * @param current  the IntermediateRepresentation that might be upcasted
     * @return the upcast IntermediateRepresentation, or the unprocessed IntermediateRepresentation if the upcaster
     *         does not support upcasting the IntermediateRepresentation's type.
     */
    @SuppressWarnings({"unchecked"})
    private List<SerializedObject> upcast(Upcaster upcaster, SerializedObject current) {
        if (upcaster.canUpcast(current.getType())) {
            current = ensureCorrectContentType(current, upcaster.expectedRepresentationType());
            return upcaster.upcast(current);
        } else {
            return Collections.singletonList(current);
        }
    }

    /**
     * Upcast the given <code>serializedType</code> to represent the type(s) of the latest revision of that object.
     * Changes in class names or packaging are typically reflected in a change in the SerializedType definition/
     *
     * @param serializedType The serialized type to upcast
     * @return The last known revision of the SerializedType of which there might be zero, one or multiple
     */
    public List<SerializedType> upcast(SerializedType serializedType) {
        return upcast(new LinkedList<Upcaster>(upcasters), serializedType);
    }

    private List<SerializedType> upcast(Queue<Upcaster> upcasterChain, SerializedType current) {
        // No upcasters are left in the queue, we're done.
        if (upcasterChain.isEmpty()) {
            return Collections.singletonList(current);
        }
        // There are still upcasters left to process.
        else {
            // Upcast the current IntermediateRepresentation.
            Upcaster upcaster = upcasterChain.poll();
            List<SerializedType> unprocessedSerializedTypes = upcast(upcaster, current);

            // Process all intermediates returned by the top upcaster exhaustively
            List<SerializedType> processedSerializedTypes = new ArrayList<SerializedType>();
            for (SerializedType unprocessedSerializedType : unprocessedSerializedTypes) {
                processedSerializedTypes.addAll(upcast(new LinkedList<Upcaster>(upcasterChain),
                                                       unprocessedSerializedType));
            }

            return processedSerializedTypes;
        }
    }

    private List<SerializedType> upcast(Upcaster upcaster, SerializedType current) {
        if (upcaster.canUpcast(current)) {
            return upcaster.upcast(current);
        } else {
            return Collections.singletonList(current);
        }
    }

    @SuppressWarnings({"unchecked"})
    private <T> SerializedObject<T> ensureCorrectContentType(SerializedObject<?> current,
                                                             Class<T> expectedContentType) {
        if (!expectedContentType.isAssignableFrom(current.getContentType())) {
            ContentTypeConverter converter = converterFactory.getConverter(current.getContentType(),
                                                                           expectedContentType);
            current = converter.convert(current);
        }
        return (SerializedObject<T>) current;
    }
}