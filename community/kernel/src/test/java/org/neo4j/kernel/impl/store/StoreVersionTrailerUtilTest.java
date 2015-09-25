/*
 * Copyright (c) 2002-2015 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.store;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;

import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.mockfs.EphemeralFileSystemAbstraction;
import org.neo4j.helpers.UTF8;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.PagedFile;
import org.neo4j.kernel.DefaultIdGeneratorFactory;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.logging.NullLogProvider;
import org.neo4j.test.EphemeralFileSystemRule;
import org.neo4j.test.PageCacheRule;
import org.neo4j.test.TargetDirectory;

import static org.junit.Assert.assertEquals;
import static org.neo4j.kernel.impl.store.CommonAbstractStore.buildTypeDescriptorAndVersion;

public class StoreVersionTrailerUtilTest
{

    @Rule
    public PageCacheRule pageCacheRule = new PageCacheRule();
    @Rule
    public EphemeralFileSystemRule fs = new EphemeralFileSystemRule();
    @Rule
    public TargetDirectory.TestDirectory dir = TargetDirectory.testDirForTestWithEphemeralFS( fs.get(), getClass() );

    private PageCache pageCache;
    private File neoStoreFile;

    @Before
    public void setUpNeoStore() throws Exception
    {
        Config config = new Config( new HashMap<String,String>(), GraphDatabaseSettings.class );
        EphemeralFileSystemAbstraction fs = this.fs.get();
        pageCache = pageCacheRule.getPageCache( fs );
        File storeDir = dir.graphDbDir();
        DefaultIdGeneratorFactory idGeneratorFactory = new DefaultIdGeneratorFactory( fs );
        NullLogProvider logProvider = NullLogProvider.getInstance();
        StoreFactory sf = new StoreFactory( storeDir, config, idGeneratorFactory, pageCache, fs, logProvider );
        try ( NeoStores neoStores = sf.openNeoStores( true ) )
        {
            neoStores.getMetaDataStore();
        }
        neoStoreFile = new File( storeDir, MetaDataStore.DEFAULT_NAME );
    }

    @Test
    public void testGetTrailerOffset() throws Exception
    {
        long trailerOffset;
        String expectedTrailer = buildTypeDescriptorAndVersion( MetaDataStore.TYPE_DESCRIPTOR );
        try ( PagedFile pagedFile = pageCache.map( neoStoreFile, pageCache.pageSize() ) )
        {
            trailerOffset = StoreVersionTrailerUtil.getTrailerPosition( pagedFile, expectedTrailer );
        }
        int expectedOffset = MetaDataStore.Position.values().length * MetaDataStore.RECORD_SIZE;
        assertEquals( expectedOffset, trailerOffset );

    }

    @Test
    public void testReadTrailer() throws Exception
    {
        String trailer;
        String expectedTrailer = buildTypeDescriptorAndVersion( MetaDataStore.TYPE_DESCRIPTOR );
        try ( PagedFile pagedFile = pageCache.map( neoStoreFile, pageCache.pageSize() ) )
        {
            trailer = StoreVersionTrailerUtil.readTrailer( pagedFile, expectedTrailer );
        }
        assertEquals( expectedTrailer, trailer );
    }

    @Test
    public void testWriteTrailer() throws Exception
    {
        String expectedTrailer = buildTypeDescriptorAndVersion( MetaDataStore.TYPE_DESCRIPTOR );
        byte[] encocdedTrailer = UTF8.encode( expectedTrailer );
        try ( PagedFile pagedFile = pageCache.map( neoStoreFile, pageCache.pageSize() ) )
        {
            long trailerOffset;
            trailerOffset = StoreVersionTrailerUtil.getTrailerPosition( pagedFile, expectedTrailer );
            StoreVersionTrailerUtil.writeTrailer( pagedFile, new byte[encocdedTrailer.length], trailerOffset );

            assertEquals( -1, StoreVersionTrailerUtil.getTrailerPosition( pagedFile, expectedTrailer ) );

            StoreVersionTrailerUtil.writeTrailer( pagedFile, encocdedTrailer, trailerOffset );
            assertEquals( trailerOffset, StoreVersionTrailerUtil.getTrailerPosition( pagedFile, expectedTrailer ) );
        }
    }
}
