package org.eblocker.server.common.blacklist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheIndexTest {

    private CacheIndex sut;

    @Nested
    class Other {
        @SuppressWarnings("DataFlowIssue")
        @Test
        void CachIndex_InsertSorted() {
            //Given
            List<CachedFileFilter> filters1 = Arrays.asList(
                    new CachedFileFilter(new CachedFilterKey(1, 3), null, null, null, false),
                    new CachedFileFilter(new CachedFilterKey(1, 2), null, null, null, false)
            );

            List<CachedFileFilter> filters2 = Arrays.asList(
                    new CachedFileFilter(new CachedFilterKey(2, 2), null, null, null, false),
                    new CachedFileFilter(new CachedFilterKey(2, 3), null, null, null, false)
            );

            Map<Integer, List<CachedFileFilter>> filters = new HashMap<>();
            filters.put(1, filters1);
            filters.put(2, filters2);

            //When
            sut = new CacheIndex(filters);

            //Then
            List<CachedFileFilter> fileFilterById1 = sut.getFileFilterById(1);
            assertEquals(filters1.get(0), fileFilterById1.get(0));
            assertEquals(filters1.get(1), fileFilterById1.get(1));

            List<CachedFileFilter> fileFilterById2 = sut.getFileFilterById(2);
            assertEquals(filters2.get(1), fileFilterById2.get(0));
            assertEquals(filters2.get(0), fileFilterById2.get(1));

        }
    }

    @Nested
    class DefaultConstructor {
        @BeforeEach
        void setUp() {
            sut = new CacheIndex();
        }

        @Test
        void getAllLatestFileFilters_emptyFilters_EmptyList() {
            //Given

            //When
            List<CachedFileFilter> allLatestFileFilters = sut.getAllLatestFileFilters();

            //Then
            assertTrue(allLatestFileFilters.isEmpty());
        }

        @SuppressWarnings("DataFlowIssue")
        @Test
        void getAllLatestFileFilters_twoIDAndTwoVersions() {
            //Given
            CachedFileFilter fileFiler1V2 = new CachedFileFilter(new CachedFilterKey(1, 2), null, null, null, false);
            CachedFileFilter fileFiler1V3 = new CachedFileFilter(new CachedFilterKey(1, 3), null, null, null, false);
            CachedFileFilter fileFiler2V2 = new CachedFileFilter(new CachedFilterKey(2, 2), null, null, null, false);
            CachedFileFilter fileFiler2V1 = new CachedFileFilter(new CachedFilterKey(2, 1), null, null, null, false);
            sut.addFilterSortedByVersion(fileFiler1V2);
            sut.addFilterSortedByVersion(fileFiler1V3);
            sut.addFilterSortedByVersion(fileFiler2V2);
            sut.addFilterSortedByVersion(fileFiler2V1);

            //When
            List<CachedFileFilter> allLatestFileFilters = sut.getAllLatestFileFilters();

            //Then
            assertEquals(2, allLatestFileFilters.size());
            assertEquals(fileFiler1V3, allLatestFileFilters.get(0));
            assertEquals(fileFiler2V2, allLatestFileFilters.get(1));
        }

        @Test
        void getAllFileFilters_emptyFileFilters_EmptyList() {
            //Given

            //When
            List<CachedFileFilter> allFileFilters = sut.getAllFileFilters();

            //Then
            assertTrue(allFileFilters.isEmpty());
        }

        @SuppressWarnings("DataFlowIssue")
        @Test
        void getAllFileFilters() {
            //Given
            CachedFileFilter fileFiler1V2 = new CachedFileFilter(new CachedFilterKey(1, 2), null, null, null, false);
            CachedFileFilter fileFiler1V3 = new CachedFileFilter(new CachedFilterKey(1, 3), null, null, null, false);
            CachedFileFilter fileFiler2V2 = new CachedFileFilter(new CachedFilterKey(2, 2), null, null, null, false);
            CachedFileFilter fileFiler2V1 = new CachedFileFilter(new CachedFilterKey(2, 1), null, null, null, false);
            sut.addFilterSortedByVersion(fileFiler1V2);
            sut.addFilterSortedByVersion(fileFiler1V3);
            sut.addFilterSortedByVersion(fileFiler2V2);
            sut.addFilterSortedByVersion(fileFiler2V1);

            //When
            List<CachedFileFilter> allFileFilters = sut.getAllFileFilters();

            //Then
            assertEquals(4, allFileFilters.size());
            assertTrue(allFileFilters.contains(fileFiler1V2));
            assertTrue(allFileFilters.contains(fileFiler1V3));
            assertTrue(allFileFilters.contains(fileFiler2V1));
            assertTrue(allFileFilters.contains(fileFiler2V2));
        }

        @Test
        void getLatestFileFilterById_unknownId() {
            //Given

            //When
            CachedFileFilter latestFileFilterById = sut.getLatestFileFilterById(12);//unknownId

            //Then
            assertNull(latestFileFilterById);
        }

        @SuppressWarnings("DataFlowIssue")
        @Test
        void getLatestFileFilterById() {
            //Given
            CachedFileFilter fileFiler1V2 = new CachedFileFilter(new CachedFilterKey(1, 2), null, null, null, false);
            CachedFileFilter fileFiler1V3 = new CachedFileFilter(new CachedFilterKey(1, 3), null, null, null, false);
            CachedFileFilter fileFiler2V1 = new CachedFileFilter(new CachedFilterKey(2, 1), null, null, null, false);
            sut.addFilterSortedByVersion(fileFiler1V2);
            sut.addFilterSortedByVersion(fileFiler1V3);
            sut.addFilterSortedByVersion(fileFiler2V1);

            //When
            CachedFileFilter latestFileFilterById = sut.getLatestFileFilterById(1);

            //Then
            assertEquals(fileFiler1V3, latestFileFilterById);
        }

        @Test
        void getFileFilterById_unknownId() {
            //Given
            //When
            List<CachedFileFilter> fileFilterById = sut.getFileFilterById(12);//unknownId

            //Then
            assertNull(fileFilterById);
        }

        @SuppressWarnings("DataFlowIssue")
        @Test
        void getFileFilterById() {
            //Given
            CachedFileFilter fileFiler1V2 = new CachedFileFilter(new CachedFilterKey(1, 2), null, null, null, false);
            CachedFileFilter fileFiler1V3 = new CachedFileFilter(new CachedFilterKey(1, 3), null, null, null, false);
            CachedFileFilter fileFiler2V1 = new CachedFileFilter(new CachedFilterKey(2, 1), null, null, null, false);
            sut.addFilterSortedByVersion(fileFiler1V2);
            sut.addFilterSortedByVersion(fileFiler1V3);
            sut.addFilterSortedByVersion(fileFiler2V1);

            //When
            List<CachedFileFilter> fileFilterById = sut.getFileFilterById(1);

            //Then
            assertEquals(2, fileFilterById.size());
            assertTrue(fileFilterById.contains(fileFiler1V2));
            assertTrue(fileFilterById.contains(fileFiler1V3));
        }

        @Test
        void removeFileFilter_unknownKey() {
            //Given
            //When
            sut.removeFileFilter(new CachedFilterKey(2, 3));

            //Then
            assertTrue(true);//No Exception
        }

        @SuppressWarnings("DataFlowIssue")
        @Test
        void removeFileFilter_keyWithTowVersions() {
            //Given
            CachedFileFilter fileFiler1V2 = new CachedFileFilter(new CachedFilterKey(1, 2), null, null, null, false);
            CachedFileFilter fileFiler1V3 = new CachedFileFilter(new CachedFilterKey(1, 3), null, null, null, false);
            CachedFileFilter fileFiler2V1 = new CachedFileFilter(new CachedFilterKey(2, 1), null, null, null, false);
            sut.addFilterSortedByVersion(fileFiler1V2);
            sut.addFilterSortedByVersion(fileFiler1V3);
            sut.addFilterSortedByVersion(fileFiler2V1);
            //When
            sut.removeFileFilter(fileFiler1V2.getKey());

            //Then
            assertEquals(2, sut.getAllFileFilters().size());
            assertEquals(1, sut.getFileFilterById(fileFiler1V3.getKey().getId()).size());
            assertEquals(fileFiler1V3, sut.getFileFilterById(fileFiler1V3.getKey().getId()).get(0));
        }

        @SuppressWarnings("DataFlowIssue")
        @Test
        void removeFileFilter_keyWithOneVersion() {
            //Given
            CachedFileFilter fileFiler1V2 = new CachedFileFilter(new CachedFilterKey(1, 2), null, null, null, false);
            CachedFileFilter fileFiler2V1 = new CachedFileFilter(new CachedFilterKey(2, 1), null, null, null, false);
            sut.addFilterSortedByVersion(fileFiler1V2);
            sut.addFilterSortedByVersion(fileFiler2V1);
            //When
            sut.removeFileFilter(fileFiler1V2.getKey());

            //Then
            assertEquals(1, sut.getAllFileFilters().size());
            assertNull(sut.getFileFilterById(fileFiler1V2.getKey().getId()));
        }

        @Test
        void isEmpty_empty() {
            //Given
            //When
            boolean empty = sut.isEmpty();

            //Then
            assertTrue(empty);
        }

        @SuppressWarnings("DataFlowIssue")
        @Test
        void isEmpty_addOne() {
            //Given
            CachedFileFilter fileFiler1V2 = new CachedFileFilter(new CachedFilterKey(1, 2), null, null, null, false);
            sut.addFilterSortedByVersion(fileFiler1V2);

            //When
            boolean empty = sut.isEmpty();

            //Then
            assertFalse(empty);
        }

        @SuppressWarnings("DataFlowIssue")
        @Test
        void isEmpty_addOneRemoveOne() {
            //Given
            CachedFileFilter fileFiler1V2 = new CachedFileFilter(new CachedFilterKey(1, 2), null, null, null, false);
            sut.addFilterSortedByVersion(fileFiler1V2);
            sut.removeFileFilter(fileFiler1V2.getKey());

            //When
            boolean empty = sut.isEmpty();

            //Then
            assertTrue(empty);
        }
    }
}