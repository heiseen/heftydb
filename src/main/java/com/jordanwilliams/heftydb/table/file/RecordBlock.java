/*
 * Copyright (c) 2013. Jordan Williams
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jordanwilliams.heftydb.table.file;

import com.jordanwilliams.heftydb.offheap.ByteMap;
import com.jordanwilliams.heftydb.offheap.Memory;
import com.jordanwilliams.heftydb.offheap.Offheap;
import com.jordanwilliams.heftydb.record.Key;
import com.jordanwilliams.heftydb.record.Record;
import com.jordanwilliams.heftydb.util.Sizes;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class RecordBlock implements Iterable<Record>, Offheap {

    public static class Builder {

        private final ByteMap.Builder byteMapBuilder = new ByteMap.Builder();
        private int sizeBytes;

        public void addRecord(Record record) {
            byteMapBuilder.add(new Key(record.key().data(), record.snapshotId()), record.value());
            sizeBytes += record.size();
        }

        public int sizeBytes() {
            return sizeBytes;
        }

        public RecordBlock build() {
            return new RecordBlock(byteMapBuilder.build());
        }
    }

    private final ByteMap byteMap;

    public RecordBlock(ByteMap byteMap) {
        this.byteMap = byteMap;
    }

    public Record get(Key key, long maxSnapshotId) {
        int closestIndex = byteMap.floorIndex(new Key(key.data(), maxSnapshotId));

        if (closestIndex < 0 || closestIndex >= byteMap.entryCount()){
            return null;
        }

        Record closestRecord = deserializeRecord(closestIndex);
        return closestRecord.key().equals(key) ? closestRecord : null;
    }

    public Record startRecord() {
        return deserializeRecord(0);
    }

    @Override
    public Iterator<Record> iterator() {

        return new Iterator<Record>() {

            int currentRecordIndex = 0;

            @Override
            public boolean hasNext() {
                return currentRecordIndex < byteMap.entryCount();
            }

            @Override
            public Record next() {
                if (currentRecordIndex >= byteMap.entryCount()) {
                    throw new NoSuchElementException();
                }

                Record next = deserializeRecord(currentRecordIndex);
                currentRecordIndex++;
                return next;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public Memory memory() {
        return byteMap.memory();
    }

    @Override
    public String toString() {
        List<Record> records = new ArrayList<Record>();
        for (Record record : this) {
            records.add(record);
        }

        return "RecordBlock{records=" + records + "}";
    }

    private Record deserializeRecord(int recordIndex) {
        ByteMap.Entry entry = byteMap.get(recordIndex);
        ByteBuffer entryKeyBuffer = entry.key().data();
        ByteBuffer recordKeyBuffer = ByteBuffer.allocate(entryKeyBuffer.capacity() - Sizes.LONG_SIZE);

        for (int i = 0; i < recordKeyBuffer.capacity(); i++){
            recordKeyBuffer.put(i, entryKeyBuffer.get(i));
        }

        long snapshotId = entryKeyBuffer.getLong(entryKeyBuffer.capacity() - Sizes.LONG_SIZE);
        return new Record(new Key(recordKeyBuffer), entry.value(), snapshotId);
    }
}
