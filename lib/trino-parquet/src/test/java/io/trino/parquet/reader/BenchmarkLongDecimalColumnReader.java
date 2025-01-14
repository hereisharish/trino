/*
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
package io.trino.parquet.reader;

import io.airlift.slice.Slice;
import io.airlift.slice.Slices;
import io.trino.parquet.PrimitiveField;
import io.trino.spi.type.DecimalType;
import org.apache.parquet.bytes.HeapByteBufferAllocator;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.values.ValuesWriter;
import org.apache.parquet.column.values.plain.FixedLenByteArrayPlainValuesWriter;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Types;

import java.util.Random;

import static io.airlift.slice.SizeOf.SIZE_OF_LONG;
import static io.trino.parquet.reader.TestData.randomBigInteger;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY;

public class BenchmarkLongDecimalColumnReader
        extends AbstractColumnReaderBenchmark<long[]>
{
    private static final int LENGTH = 2 * SIZE_OF_LONG;

    private final Random random = new Random(1);

    @Override
    protected PrimitiveField createPrimitiveField()
    {
        PrimitiveType parquetType = Types.optional(FIXED_LEN_BYTE_ARRAY)
                .length(LENGTH)
                .as(LogicalTypeAnnotation.decimalType(0, 38))
                .named("name");
        return new PrimitiveField(
                DecimalType.createDecimalType(38),
                true,
                new ColumnDescriptor(new String[] {"test"}, parquetType, 0, 0),
                0);
    }

    @Override
    protected ValuesWriter createValuesWriter(int bufferSize)
    {
        return new FixedLenByteArrayPlainValuesWriter(LENGTH, bufferSize, bufferSize, HeapByteBufferAllocator.getInstance());
    }

    @Override
    protected void writeValue(ValuesWriter writer, long[] batch, int index)
    {
        Slice slice = Slices.wrappedLongArray(batch, index * 2, 2);
        writer.writeBytes(Binary.fromConstantByteArray(slice.getBytes()));
    }

    @Override
    protected long[] generateDataBatch(int size)
    {
        long[] batch = new long[size * 2];
        for (int i = 0; i < size; i++) {
            Slice slice = randomBigInteger(random);
            batch[i * 2] = slice.getLong(0);
            batch[(i * 2) + 1] = slice.getLong(SIZE_OF_LONG);
        }
        return batch;
    }

    public static void main(String[] args)
            throws Exception
    {
        run(BenchmarkLongDecimalColumnReader.class);
    }
}
