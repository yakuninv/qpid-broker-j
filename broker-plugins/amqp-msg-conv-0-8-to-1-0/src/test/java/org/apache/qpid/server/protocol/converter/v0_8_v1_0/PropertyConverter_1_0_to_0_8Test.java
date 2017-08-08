/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.protocol.converter.v0_8_v1_0;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.NamedAddressSpace;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.protocol.converter.MessageConversionException;
import org.apache.qpid.server.protocol.v0_8.AMQMessage;
import org.apache.qpid.server.protocol.v0_8.AMQShortString;
import org.apache.qpid.server.protocol.v0_8.FieldTable;
import org.apache.qpid.server.protocol.v0_8.transport.BasicContentHeaderProperties;
import org.apache.qpid.server.protocol.v0_8.transport.MessagePublishInfo;
import org.apache.qpid.server.protocol.v1_0.MessageMetaData_1_0;
import org.apache.qpid.server.protocol.v1_0.Message_1_0;
import org.apache.qpid.server.protocol.v1_0.type.Binary;
import org.apache.qpid.server.protocol.v1_0.type.Symbol;
import org.apache.qpid.server.protocol.v1_0.type.UnsignedByte;
import org.apache.qpid.server.protocol.v1_0.type.UnsignedInteger;
import org.apache.qpid.server.protocol.v1_0.type.UnsignedLong;
import org.apache.qpid.server.protocol.v1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.server.protocol.v1_0.type.messaging.DeliveryAnnotations;
import org.apache.qpid.server.protocol.v1_0.type.messaging.Footer;
import org.apache.qpid.server.protocol.v1_0.type.messaging.Header;
import org.apache.qpid.server.protocol.v1_0.type.messaging.MessageAnnotations;
import org.apache.qpid.server.protocol.v1_0.type.messaging.Properties;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.test.utils.QpidTestCase;

public class PropertyConverter_1_0_to_0_8Test extends QpidTestCase
{
    private NamedAddressSpace _namedAddressSpace;
    private MessageConverter_1_0_to_v0_8 _messageConverter;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _namedAddressSpace = mock(NamedAddressSpace.class);
        _messageConverter = new MessageConverter_1_0_to_v0_8();
    }

    public void testContentEncodingConversion()
    {

        String contentEncoding = "my-test-encoding";
        final Properties properties = new Properties();
        properties.setContentEncoding(Symbol.valueOf(contentEncoding));
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected content encoding", contentEncoding, convertedProperties.getEncoding().toString());
    }

    public void testContentEncodingConversionWhenLengthExceeds255()
    {

        String contentEncoding = generateLongString();
        final Properties properties = new Properties();
        properties.setContentEncoding(Symbol.valueOf(contentEncoding));
        Message_1_0 message = createTestMessage(properties);

        try
        {
            _messageConverter.convert(message, _namedAddressSpace);
            fail("expected exception not thrown");
        }
        catch (MessageConversionException e)
        {
            // pass
        }
    }

    public void testApplicationPropertiesConversion()
    {
        Map<String, Object> properties = new HashMap<>();
        properties.put("testProperty1", "testProperty1Value");
        properties.put("intProperty", 1);
        ApplicationProperties applicationProperties = new ApplicationProperties(properties);
        Message_1_0 message = createTestMessage(applicationProperties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        final Map<String, Object> headers = FieldTable.convertToMap(convertedProperties.getHeaders());
        assertEquals("Unexpected headers", properties, new HashMap<>(headers));
    }

    public void testSubjectConversion()
    {
        final String subject = "testSubject";
        Properties properties = new Properties();
        properties.setSubject(subject);
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        final Map<String, Object> headers = FieldTable.convertToMap(convertedProperties.getHeaders());
        assertEquals("Unexpected qpid.subject is missing from headers", subject, headers.get("qpid.subject"));
        assertEquals("Unexpected type", subject, convertedProperties.getType().toString());
        final MessagePublishInfo messagePublishInfo = convertedMessage.getMessagePublishInfo();
        assertEquals("Unexpected routing-key", subject, messagePublishInfo.getRoutingKey().toString());
    }

    public void testSubjectConversionWhenSubjectExceeds255()
    {
        final String subject = generateLongString();
        Properties properties = new Properties();
        properties.setSubject(subject);
        Message_1_0 message = createTestMessage(properties);

        try
        {
            _messageConverter.convert(message, _namedAddressSpace);
            fail("Expected conversion exception");
        }
        catch (MessageConversionException e)
        {
            // pass
        }
    }

    public void testDurableConversion()
    {
        final Header header = new Header();
        header.setDurable(true);
        Message_1_0 message = createTestMessage(header);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected deliveryMode",
                     BasicContentHeaderProperties.PERSISTENT,
                     convertedProperties.getDeliveryMode());
    }

    public void testNonDurableConversion()
    {
        final Header header = new Header();
        header.setDurable(false);
        Message_1_0 message = createTestMessage(header);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected deliveryMode",
                     BasicContentHeaderProperties.NON_PERSISTENT,
                     convertedProperties.getDeliveryMode());
    }

    public void testPriorityConversion()
    {
        final Header header = new Header();
        final byte priority = (byte) 7;
        header.setPriority(UnsignedByte.valueOf(priority));
        Message_1_0 message = createTestMessage(header);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected priority", priority, convertedProperties.getPriority());
    }

    public void testCorrelationIdStringConversion()
    {
        final String correlationId = "testCorrelationId";
        Properties properties = new Properties();
        properties.setCorrelationId(correlationId);
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected correlationId", correlationId, convertedProperties.getCorrelationId().toString());
    }

    public void testCorrelationIdLongStringConversion()
    {
        final String correlationId = generateLongString();
        Properties properties = new Properties();
        properties.setCorrelationId(correlationId);
        Message_1_0 message = createTestMessage(properties);

        try
        {
            _messageConverter.convert(message, _namedAddressSpace);
            fail("Expected exception not thrown");
        }
        catch (MessageConversionException e)
        {
            // pass
        }
    }

    public void testCorrelationIdULongConversion()
    {
        final UnsignedLong correlationId = UnsignedLong.valueOf(-1);
        Properties properties = new Properties();
        properties.setCorrelationId(correlationId);
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected correlationId",
                     correlationId.toString(),
                     convertedProperties.getCorrelationId().toString());
    }

    public void testCorrelationIdUUIDConversion()
    {
        final UUID correlationId = UUID.randomUUID();
        Properties properties = new Properties();
        properties.setCorrelationId(correlationId);
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected correlationId",
                     correlationId.toString(),
                     convertedProperties.getCorrelationId().toString());
    }

    public void testCorrelationIdBinaryConversion()
    {
        final String testCorrelationId = "testCorrelationId";
        final Binary correlationId = new Binary(testCorrelationId.getBytes(UTF_8));
        Properties properties = new Properties();
        properties.setCorrelationId(correlationId);
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected correlationId", testCorrelationId, convertedProperties.getCorrelationId().toString());
    }

    public void testCorrelationIdBinaryConversionWhenNotUtf8()
    {
        final byte[] testCorrelationId = new byte[]{(byte) 0xc3, 0x28};
        final Binary correlationId = new Binary(testCorrelationId);
        Properties properties = new Properties();
        properties.setCorrelationId(correlationId);
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertTrue("Unexpected correlationId",
                   Arrays.equals(testCorrelationId, convertedProperties.getCorrelationId().getBytes()));
    }

    public void testReplyToConversionWhenResultExceeds255()
    {
        final String replyTo = generateLongString() + "/" + generateLongString();
        Properties properties = new Properties();
        properties.setReplyTo(replyTo);
        Message_1_0 message = createTestMessage(properties);

        try
        {
            _messageConverter.convert(message, _namedAddressSpace);
            fail("expected exception not thrown");
        }
        catch (MessageConversionException e)
        {
            // pass
        }
    }

    public void testTTLConversion()
    {
        long ttl = 10000;
        long arrivalTime = System.currentTimeMillis();
        long expectedExpiration = arrivalTime + ttl;
        Header header = new Header();
        header.setTtl(UnsignedInteger.valueOf(ttl));
        Message_1_0 message = createTestMessage(header, arrivalTime);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected expiration", expectedExpiration, convertedProperties.getExpiration());
    }

    public void testAbsoluteExpiryTimeConversion()
    {
        long ttl = 10000;
        long arrivalTime = System.currentTimeMillis();
        long expiryTime = arrivalTime + ttl;
        Properties properties = new Properties();
        properties.setAbsoluteExpiryTime(new Date(expiryTime));
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected expiration", expiryTime, convertedProperties.getExpiration());
    }

    public void testConversionOfAbsoluteExpiryTimeTakesPrecedenceOverTTL()
    {
        long ttl = 10000;
        final long time = System.currentTimeMillis();
        long absoluteExpiryTime = time + ttl;
        long arrivalTime = time + 1;

        Header header = new Header();
        header.setTtl(UnsignedInteger.valueOf(ttl));

        Properties properties = new Properties();
        properties.setAbsoluteExpiryTime(new Date(absoluteExpiryTime));

        Message_1_0 message = createTestMessage(header,
                                                new DeliveryAnnotations(Collections.emptyMap()),
                                                new MessageAnnotations(Collections.emptyMap()),
                                                properties,
                                                new ApplicationProperties(Collections.emptyMap()),
                                                arrivalTime
                                               );

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected expiration", absoluteExpiryTime, convertedProperties.getExpiration());
    }

    public void testMessageIdStringConversion()
    {
        final String messageId = "testMessageId";
        Properties properties = new Properties();
        properties.setMessageId(messageId);
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected messageId", messageId, convertedProperties.getMessageId().toString());
    }

    public void testMessageIdLongStringConversion()
    {
        final String messageId = generateLongString();
        Properties properties = new Properties();
        properties.setMessageId(messageId);
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertNull("Unexpected messageId", convertedProperties.getMessageId());
    }

    public void testMessageIdUUIDConversion()
    {
        final UUID messageId = UUID.randomUUID();
        Properties properties = new Properties();
        properties.setMessageId(messageId);
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected messageId", messageId.toString(), convertedProperties.getMessageId().toString());
    }

    public void testMessageIdUnsignedLongConversion()
    {
        final UnsignedLong messageId = UnsignedLong.valueOf(-1);
        Properties properties = new Properties();
        properties.setMessageId(messageId);
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected messageId", messageId.toString(), convertedProperties.getMessageId().toString());
    }

    public void testMessageIdBinaryConversion()
    {
        final String messageId = "testMessageId";
        Properties properties = new Properties();
        properties.setMessageId(new Binary(messageId.getBytes()));
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected messageId", messageId, convertedProperties.getMessageId().toString());
    }

    public void testMessageIdByteArrayConversion()
    {
        final byte[] messageId = "testMessageId".getBytes();
        Properties properties = new Properties();
        properties.setMessageId(messageId);
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertTrue("Unexpected messageId", Arrays.equals(messageId, convertedProperties.getMessageId().getBytes()));
    }

    public void testMessageIdBinaryConversionWhenNonUtf8()
    {
        final byte[] messageId = new byte[]{(byte) 0xc3, 0x28};
        Properties properties = new Properties();
        properties.setMessageId(new Binary(messageId));
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertTrue("Unexpected messageId", Arrays.equals(messageId, convertedProperties.getMessageId().getBytes()));
    }

    public void testCreationTimeConversion()
    {
        final long timestamp = System.currentTimeMillis() - 10000;
        final long arrivalTime = timestamp + 1;
        Properties properties = new Properties();
        properties.setCreationTime(new Date(timestamp));
        Message_1_0 message = createTestMessage(new Header(),
                                                new DeliveryAnnotations(Collections.emptyMap()),
                                                new MessageAnnotations(Collections.emptyMap()),
                                                properties,
                                                new ApplicationProperties(Collections.emptyMap()),
                                                arrivalTime
                                               );

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected timestamp", timestamp, convertedProperties.getTimestamp());
    }

    public void testArrivalTimeConversion()
    {
        final long arrivalTime = System.currentTimeMillis() - 10000;
        Message_1_0 message = createTestMessage(new Header(), arrivalTime);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected timestamp", arrivalTime, convertedProperties.getTimestamp());
    }

    public void testUserIdConversion()
    {
        final String userId = "test-userId";
        Properties properties = new Properties();
        properties.setUserId(new Binary(userId.getBytes()));
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertEquals("Unexpected user-id", userId, convertedProperties.getUserIdAsString());
    }

    public void testUserIdConversionWhenLengthExceeds255()
    {
        final String userId = generateLongString();
        Properties properties = new Properties();
        properties.setUserId(new Binary(userId.getBytes()));
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertNull("Unexpected user-id", convertedProperties.getUserId());
    }

    public void testUserIdConversionWhenNonUtf8()
    {
        final byte[] userId = new byte[]{(byte) 0xc3, 0x28};
        Properties properties = new Properties();
        properties.setUserId(new Binary(userId));
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        assertTrue("Unexpected user-id", Arrays.equals(userId, convertedProperties.getUserId().getBytes()));
    }

    public void testGroupIdConversion()
    {
        String testGroupId = generateLongString();
        Properties properties = new Properties();
        properties.setGroupId(testGroupId);
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        Map<String, Object> headers = FieldTable.convertToMap(convertedProperties.getHeaders());
        assertEquals("Unexpected group-id", testGroupId, headers.get("JMSXGroupID"));
    }

    public void testGroupSequenceConversion()
    {
        int testGroupSequence = 1;
        Properties properties = new Properties();
        properties.setGroupSequence(UnsignedInteger.valueOf(testGroupSequence));
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        BasicContentHeaderProperties convertedProperties = convertedMessage.getContentHeaderBody().getProperties();
        Map<String, Object> headers = FieldTable.convertToMap(convertedProperties.getHeaders());
        assertEquals("Unexpected group-id", testGroupSequence, headers.get("JMSXGroupSeq"));
    }


    public void testApplicationPropertiesConversionWhenKeyLengthExceeds255()
    {
        Map<String, Object> properties = Collections.singletonMap("testProperty-" + generateLongString(), "testValue");
        ApplicationProperties applicationProperties = new ApplicationProperties(properties);
        Message_1_0 message = createTestMessage(applicationProperties);

        try
        {
            _messageConverter.convert(message, _namedAddressSpace);
            fail("Exception is expected");
        }
        catch (MessageConversionException e)
        {
            // pass
        }
    }

    public void testToConversionWhenExchangeAndRoutingKeyIsSpecified()
    {
        final String testExchange = "testExchange";
        final String testRoutingKey = "testRoutingKey";

        String to = testExchange + "/" + testRoutingKey;
        Properties properties = new Properties();
        properties.setTo(to);
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        final MessagePublishInfo messagePublishInfo = convertedMessage.getMessagePublishInfo();

        assertEquals("Unexpected exchange", testExchange, messagePublishInfo.getExchange().toString());
        assertEquals("Unexpected routing key", testRoutingKey, messagePublishInfo.getRoutingKey().toString());
    }

    public void testToConversionWhenExchangeIsSpecified()
    {
        final String testExchange = "testExchange";
        Properties properties = new Properties();
        properties.setTo(testExchange);
        Message_1_0 message = createTestMessage(properties);

        when(_namedAddressSpace.getAttainedMessageDestination(testExchange)).thenReturn(mock(Exchange.class));

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        final MessagePublishInfo messagePublishInfo = convertedMessage.getMessagePublishInfo();

        assertEquals("Unexpected exchange", testExchange, messagePublishInfo.getExchange().toString());
        assertEquals("Unexpected routing key", "", messagePublishInfo.getRoutingKey().toString());
    }

    public void testToConversionWhenExchangeIsSpecifiedAndSubjectIsSet()
    {
        final String testExchange = "testExchange";
        final String testRoutingKey = "testRoutingKey";
        Properties properties = new Properties();
        properties.setTo(testExchange);
        properties.setSubject(testRoutingKey);
        Message_1_0 message = createTestMessage(properties);

        when(_namedAddressSpace.getAttainedMessageDestination(testExchange)).thenReturn(mock(Exchange.class));

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        final MessagePublishInfo messagePublishInfo = convertedMessage.getMessagePublishInfo();

        assertEquals("Unexpected exchange", testExchange, messagePublishInfo.getExchange().toString());
        assertEquals("Unexpected routing key", testRoutingKey, messagePublishInfo.getRoutingKey().toString());
    }

    public void testToConversionWhenQueueIsSpecified()
    {
        final String testQueue = "testQueue";
        Properties properties = new Properties();
        properties.setTo(testQueue);
        Message_1_0 message = createTestMessage(properties);

        when(_namedAddressSpace.getAttainedMessageDestination(testQueue)).thenReturn(mock(Queue.class));

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        final MessagePublishInfo messagePublishInfo = convertedMessage.getMessagePublishInfo();

        assertEquals("Unexpected exchange", "", messagePublishInfo.getExchange().toString());
        assertEquals("Unexpected routing key", testQueue, messagePublishInfo.getRoutingKey().toString());
    }

    public void testToConversionWhenGlobalAddress()
    {
        final String globalAddress = "/testQueue";
        Properties properties = new Properties();
        properties.setTo(globalAddress);
        Message_1_0 message = createTestMessage(properties);

        try
        {
            _messageConverter.convert(message, _namedAddressSpace);
            fail("Exception is not thrown");
        }
        catch (MessageConversionException e)
        {
            // pass
        }
    }

    public void testToConversionWhenExchangeLengthExceeds255()
    {
        final String testExchange = generateLongString();
        final String testRoutingKey = "testRoutingKey";

        String to = testExchange + "/" + testRoutingKey;
        Properties properties = new Properties();
        properties.setTo(to);
        Message_1_0 message = createTestMessage(properties);

        try
        {
            _messageConverter.convert(message, _namedAddressSpace);
            fail("Exception is not thrown");
        }
        catch (MessageConversionException e)
        {
            // pass
        }
    }

    public void testToConversionWhenRoutingKeyLengthExceeds255()
    {
        final String testExchange = "testExchange";
        final String testRoutingKey = generateLongString();

        String to = testExchange + "/" + testRoutingKey;
        Properties properties = new Properties();
        properties.setTo(to);
        Message_1_0 message = createTestMessage(properties);

        try
        {
            _messageConverter.convert(message, _namedAddressSpace);
            fail("Exception is not thrown");
        }
        catch (MessageConversionException e)
        {
            // pass
        }
    }

    public void testToConversionWhenDestinationIsSpecifiedButDoesNotExists()
    {
        final String testDestination = "testDestination";
        Properties properties = new Properties();
        properties.setTo(testDestination);
        Message_1_0 message = createTestMessage(properties);

        final AMQMessage convertedMessage = _messageConverter.convert(message, _namedAddressSpace);

        final MessagePublishInfo messagePublishInfo = convertedMessage.getMessagePublishInfo();

        assertEquals("Unexpected exchange", testDestination, messagePublishInfo.getExchange().toString());
        assertEquals("Unexpected routing key", "", messagePublishInfo.getRoutingKey().toString());
    }

    private Message_1_0 createTestMessage(final Header header)
    {
        return createTestMessage(header, 0);
    }

    private Message_1_0 createTestMessage(final Header header, long arrivalTime)
    {
        return createTestMessage(header,
                                 new DeliveryAnnotations(Collections.emptyMap()),
                                 new MessageAnnotations(Collections.emptyMap()),
                                 new Properties(),
                                 new ApplicationProperties(Collections.emptyMap()),
                                 arrivalTime
                                );
    }

    private Message_1_0 createTestMessage(final Properties properties)
    {
        return createTestMessage(new Header(),
                                 new DeliveryAnnotations(Collections.emptyMap()),
                                 new MessageAnnotations(Collections.emptyMap()),
                                 properties,
                                 new ApplicationProperties(Collections.emptyMap()),
                                 0
                                );
    }

    private Message_1_0 createTestMessage(final ApplicationProperties applicationProperties)
    {
        return createTestMessage(new Header(),
                                 new DeliveryAnnotations(Collections.emptyMap()),
                                 new MessageAnnotations(Collections.emptyMap()),
                                 new Properties(),
                                 applicationProperties,
                                 0
                                );
    }

    private Message_1_0 createTestMessage(final Header header,
                                          final DeliveryAnnotations deliveryAnnotations,
                                          final MessageAnnotations messageAnnotations,
                                          final Properties properties,
                                          final ApplicationProperties applicationProperties,
                                          final long arrivalTime)
    {
        final StoredMessage<MessageMetaData_1_0> storedMessage = mock(StoredMessage.class);
        MessageMetaData_1_0 metaData = new MessageMetaData_1_0(header.createEncodingRetainingSection(),
                                                               deliveryAnnotations.createEncodingRetainingSection(),
                                                               messageAnnotations.createEncodingRetainingSection(),
                                                               properties.createEncodingRetainingSection(),
                                                               applicationProperties.createEncodingRetainingSection(),
                                                               new Footer(Collections.emptyMap()).createEncodingRetainingSection(),
                                                               arrivalTime,
                                                               0);
        when(storedMessage.getMetaData()).thenReturn(metaData);
        return new Message_1_0(storedMessage);
    }

    private String generateLongString()
    {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < AMQShortString.MAX_LENGTH + 1; i++)
        {
            buffer.append('x');
        }

        return buffer.toString();
    }
}