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
package org.neo4j.cluster.protocol.atomicbroadcast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Stream factory for serializing/deserializing messages.
 */
public class ObjectStreamFactory implements ObjectInputStreamFactory, ObjectOutputStreamFactory
{
    private final VersionMapper versionMapper;

    public ObjectStreamFactory()
    {
        versionMapper = new VersionMapper();
    }

    @Override
    public ObjectOutputStream create( ByteArrayOutputStream bout ) throws IOException
    {
        return new LenientObjectOutputStream( bout, versionMapper );
    }

    @Override
    public ObjectInputStream create( ByteArrayInputStream in ) throws IOException
    {
        return new LenientObjectInputStream( in, versionMapper );
    }
}