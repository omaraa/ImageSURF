/*
 * Copyright (c) 2018 Goldman Sachs and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v. 1.0 which accompany this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */

package imagesurf.feature.calculator;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * This code was adapted from Eclipse Collections IntHashSet
 */

public class IntHashSet {
    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final int EMPTY = 0;
    private static final int REMOVED = 1;
    private static final int CACHE_LINE_SIZE = 64;
    private static final int KEY_SIZE = 4;
    private static final int INITIAL_LINEAR_PROBE = CACHE_LINE_SIZE / KEY_SIZE / 2; /* half a cache line */

    private int[] table;
    private int occupiedWithData;
    private int occupiedWithSentinels;
    // The 32 bits of this integer indicate whether the items 0 to 31 are present in the set.
    private int zeroToThirtyOne;
    private int zeroToThirtyOneOccupied;
    private transient boolean copyOnWrite;

    public IntHashSet()
    {
        this.allocateTable(DEFAULT_INITIAL_CAPACITY);
    }

    public IntHashSet(int initialCapacity)
    {
        if (initialCapacity < 0)
        {
            throw new IllegalArgumentException("initial capacity cannot be less than 0");
        }
        int capacity = this.smallestPowerOfTwoGreaterThan(initialCapacity << 1);
        this.allocateTable(capacity);
    }

    private int smallestPowerOfTwoGreaterThan(int n)
    {
        return n > 1 ? Integer.highestOneBit(n - 1) << 1 : 1;
    }

    private static boolean isBetweenZeroAndThirtyOne(int value)
    {
        return value >= 0 && value <= 31;
    }

    @SuppressWarnings("AbstractMethodOverridesAbstractMethod")
    @Override
    public int hashCode()
    {
        int result = 0;
        int zeroToThirtyOne = this.zeroToThirtyOne;
        while (zeroToThirtyOne != 0)
        {
            int value = Integer.numberOfTrailingZeros(zeroToThirtyOne);
            result += value;
            zeroToThirtyOne &= ~(1 << value);
        }
        if (this.table != null)
        {
            for (int i = 0; i < this.table.length; i++)
            {
                if (isNonSentinel(this.table[i]))
                {
                    result += this.table[i];
                }
            }
        }
        return result;
    }

    public int size()
    {
        return this.occupiedWithData + this.zeroToThirtyOneOccupied;
    }

    public boolean add(int element)
    {
        if (isBetweenZeroAndThirtyOne(element))
        {
            int initial = this.zeroToThirtyOne;
            this.zeroToThirtyOne |= 1 << element;
            if (this.zeroToThirtyOne != initial)
            {
                this.zeroToThirtyOneOccupied++;
                return true;
            }
            return false;
        }

        int index = this.probe(element);

        if (this.table[index] == element)
        {
            // element already present in set
            return false;
        }

        if (this.copyOnWrite)
        {
            this.copyTable();
        }
        if (this.table[index] == REMOVED)
        {
            --this.occupiedWithSentinels;
        }
        this.table[index] = element;
        ++this.occupiedWithData;
        if (this.occupiedWithData + this.occupiedWithSentinels > this.maxOccupiedWithData())
        {
            this.rehashAndGrow();
        }
        return true;
    }

    public boolean remove(int value)
    {
        if (isBetweenZeroAndThirtyOne(value))
        {
            return this.removeZeroToThirtyOne(value);
        }
        if (this.occupiedWithData == 0)
        {
            return false;
        }
        int index = this.probe(value);
        if (this.table[index] == value)
        {
            if (this.copyOnWrite)
            {
                this.copyTable();
            }
            this.table[index] = REMOVED;
            this.occupiedWithData--;
            this.occupiedWithSentinels++;
            return true;
        }
        return false;
    }

    private boolean removeZeroToThirtyOne(int value)
    {
        int initial = this.zeroToThirtyOne;
        this.zeroToThirtyOne &= ~(1 << value);
        if (this.zeroToThirtyOne == initial)
        {
            return false;
        }
        this.zeroToThirtyOneOccupied--;
        return true;
    }

    public void clear()
    {
        this.zeroToThirtyOneOccupied = 0;
        this.occupiedWithData = 0;
        this.occupiedWithSentinels = 0;

        this.zeroToThirtyOne = 0;
        if (this.copyOnWrite)
        {
            this.table = new int[this.table.length];
            this.copyOnWrite = false;
        }
        else
        {
            Arrays.fill(this.table, EMPTY);
        }
    }

    public IntIterator intIterator()
    {
        return new IntIterator();
    }

    public boolean contains(int value)
    {
        if (isBetweenZeroAndThirtyOne(value))
        {
            int temp = this.zeroToThirtyOne;
            return ((temp >>> value) & 1) != 0;
        }
        return this.table[this.probe(value)] == value;
    }

    private void rehashAndGrow()
    {
        int max = this.maxOccupiedWithData();
        int newCapacity = Math.max(max, smallestPowerOfTwoGreaterThan((this.occupiedWithData + 1) << 1));
        if (this.occupiedWithSentinels > 0 && (max >> 1) + (max >> 2) < this.occupiedWithData)
        {
            newCapacity <<= 1;
        }
        this.rehash(newCapacity);
    }

    private void rehash(int newCapacity)
    {
        int oldLength = this.table.length;
        int[] old = this.table;
        this.allocateTable(newCapacity);
        this.occupiedWithData = 0;
        this.occupiedWithSentinels = 0;

        for (int i = 0; i < oldLength; i++)
        {
            if (isNonSentinel(old[i]))
            {
                this.add(old[i]);
            }
        }
    }

    protected void allocateTable(int sizeToAllocate)
    {
        this.table = new int[sizeToAllocate];
    }

    // exposed for testing
    int probe(int element)
    {
        int index = this.spreadAndMask(element);
        int valueAtIndex = this.table[index];

        if (valueAtIndex == element || valueAtIndex == EMPTY)
        {
            return index;
        }

        int removedIndex = valueAtIndex == REMOVED ? index : -1;
        for (int i = 1; i < INITIAL_LINEAR_PROBE; i++)
        {
            int nextIndex = (index + i) & (this.table.length - 1);
            valueAtIndex = this.table[nextIndex];
            if (valueAtIndex == element)
            {
                return nextIndex;
            }
            if (valueAtIndex == EMPTY)
            {
                return removedIndex == -1 ? nextIndex : removedIndex;
            }
            if (valueAtIndex == REMOVED && removedIndex == -1)
            {
                removedIndex = nextIndex;
            }
        }
        return this.probeTwo(element, removedIndex);
    }

    int probeTwo(int element, int removedIndex)
    {
        int index = this.spreadTwoAndMask(element);
        for (int i = 0; i < INITIAL_LINEAR_PROBE; i++)
        {
            int nextIndex = (index + i) & (this.table.length - 1);
            int valueAtIndex = this.table[nextIndex];
            if (valueAtIndex == element)
            {
                return nextIndex;
            }
            if (valueAtIndex == EMPTY)
            {
                return removedIndex == -1 ? nextIndex : removedIndex;
            }
            if (valueAtIndex == REMOVED && removedIndex == -1)
            {
                removedIndex = nextIndex;
            }
        }
        return this.probeThree(element, removedIndex);
    }

    public static int intSpreadOne(int element)
    {
        int code1 = element;
        code1 ^= code1 >>> 15;
        code1 *= 0xACAB2A4D;
        code1 ^= code1 >>> 15;
        code1 *= 0x5CC7DF53;
        code1 ^= code1 >>> 12;
        return code1;
    }

    public static int intSpreadTwo(int element)
    {
        int code1 = element;
        code1 ^= code1 >>> 14;
        code1 *= 0xBA1CCD33;
        code1 ^= code1 >>> 13;
        code1 *= 0x9B6296CB;
        code1 ^= code1 >>> 12;
        return code1;
    }


    int probeThree(int element, int removedIndex)
    {
        int nextIndex = Integer.reverse(intSpreadOne(element));
        int spreadTwo = Integer.reverse(intSpreadTwo(element)) | 1;

        while (true)
        {
            nextIndex = this.mask(nextIndex + spreadTwo);
            int valueAtIndex = this.table[nextIndex];
            if (valueAtIndex == element)
            {
                return nextIndex;
            }
            if (valueAtIndex == EMPTY)
            {
                return removedIndex == -1 ? nextIndex : removedIndex;
            }
            if (valueAtIndex == REMOVED && removedIndex == -1)
            {
                removedIndex = nextIndex;
            }
        }
    }

    // exposed for testing
    int spreadAndMask(int element)
    {
        int code = intSpreadOne(element);
        return this.mask(code);
    }

    int spreadTwoAndMask(int element)
    {
        int code = intSpreadTwo(element);
        return this.mask(code);
    }

    private int mask(int spread)
    {
        return spread & (this.table.length - 1);
    }

    private void copyTable()
    {
        this.copyOnWrite = false;
        int[] copy = new int[this.table.length];
        System.arraycopy(this.table, 0, copy, 0, this.table.length);
        this.table = copy;
    }

    private int maxOccupiedWithData()
    {
        return this.table.length >> 1;
    }

    private static boolean isNonSentinel(int value)
    {
        return value != EMPTY && value != REMOVED;
    }


    public class IntIterator {
        private int count;
        private int position;
        private int zeroToThirtyOne;

        public boolean hasNext()
        {
            return this.count < IntHashSet.this.size();
        }

        public int next()
        {
            if (!this.hasNext())
            {
                throw new NoSuchElementException("next() called, but the iterator is exhausted");
            }
            this.count++;

            while (this.zeroToThirtyOne < 32)
            {
                if (IntHashSet.this.contains(this.zeroToThirtyOne))
                {
                    int result = this.zeroToThirtyOne;
                    this.zeroToThirtyOne++;
                    return result;
                }
                this.zeroToThirtyOne++;
            }

            int[] table = IntHashSet.this.table;
            while (!isNonSentinel(table[this.position]))
            {
                this.position++;
            }
            int result = table[this.position];
            this.position++;
            return result;
        }
    }
}
