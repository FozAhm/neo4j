/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j Enterprise Edition. The included source
 * code can be redistributed and/or modified under the terms of the
 * Neo4j Sweden Software License, as found in the associated LICENSE.txt
 * file.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Neo4j Sweden Software License for more details.
 */
package org.neo4j.com;

import java.io.Flushable;
import java.io.IOException;

import org.jboss.netty.buffer.ChannelBuffer;

import org.neo4j.kernel.impl.transaction.log.FlushableChannel;

public class NetworkFlushableChannel implements Flushable, FlushableChannel
{
    private final ChannelBuffer delegate;

    public NetworkFlushableChannel( ChannelBuffer delegate )
    {
        this.delegate = delegate;
    }

    @Override
    public void flush()
    {
    }

    @Override
    public FlushableChannel put( byte value )
    {
        delegate.writeByte( value );
        return this;
    }

    @Override
    public FlushableChannel putShort( short value )
    {
        delegate.writeShort( value );
        return this;
    }

    @Override
    public FlushableChannel putInt( int value )
    {
        delegate.writeInt( value );
        return this;
    }

    @Override
    public FlushableChannel putLong( long value )
    {
        delegate.writeLong( value );
        return this;
    }

    @Override
    public FlushableChannel putFloat( float value )
    {
        delegate.writeFloat( value );
        return this;
    }

    @Override
    public FlushableChannel putDouble( double value )
    {
        delegate.writeDouble( value );
        return this;
    }

    @Override
    public FlushableChannel put( byte[] value, int length )
    {
        delegate.writeBytes( value, 0, length );
        return this;
    }

    @Override
    public void close()
    {
    }

    @Override
    public Flushable prepareForFlush()
    {
        return this;
    }
}