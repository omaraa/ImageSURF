package imagesurf.feature.calculator.histogram;

import it.unimi.dsi.util.XorShift1024StarPhiRandom;

import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

/**
 * A skip list is a data structure that allows fast search within
 * an ordered sequence of elements. Fast search is made possible
 * by maintaining a linked hierarchy of subsequences, with each
 * successive subsequence skipping over fewer elements than the
 * previous one. Searching starts in the sparsest subsequence until
 * two consecutive elements have been found, one smaller and one larger
 * than or equal to the element searched for.
 * <p>
 * cite: <a href="https://en.wikipedia.org/wiki/Skip_list">Skip List - Wikipedia</a>
 * <br>
 *
 * @author SylvanasSun <sylvanas.sun@gmail.com>
 */
class SkipList {

    static final int HEAD_VALUE = Integer.MIN_VALUE;

    protected static final XorShift1024StarPhiRandom randomGenerator = new XorShift1024StarPhiRandom();

    protected static final double DEFAULT_PROBABILITY = 0.8;

    private Node head;

    private double probability;

    private int size;

    public SkipList() {
        this(DEFAULT_PROBABILITY);
    }

    public SkipList(double probability) {
        this.head = new Node(HEAD_VALUE, 0);
        this.probability = probability;
        this.size = 0;
    }

    public void clear() {
        this.head = new Node(HEAD_VALUE, 0);
        this.size = 0;
    }

    public boolean contains(int value) {
        Node node = findNode(value);
        return node != null && node.value == value;
    }

    public void add(int value) {
        Node node = findNode(value);

        if (node.value == value) {
            return;
        }

        Node newNode = new Node(value, node.level);
        horizontalInsert(node, newNode);
        // Decide level according to the probability function
        int currentLevel = node.level;
        int headLevel = head.level;
        while (isBuildLevel()) {
            // buiding a new level
            if (currentLevel >= headLevel) {
                Node newHead = new Node(HEAD_VALUE, headLevel + 1);
                verticalLink(newHead, head);
                head = newHead;
                headLevel = head.level;
            }
            // copy node and newNode to the upper level
            while (node.up == null) {
                node = node.previous;
            }
            node = node.up;

            Node tmp = new Node(value, node.level);
            horizontalInsert(node, tmp);
            verticalLink(tmp, newNode);
            newNode = tmp;
            currentLevel++;
        }
        size++;
    }

    public void remove(int value) {
        Node node = findNode(value);
        if (node == null || node.value != value)
            throw new NoSuchElementException("The key is not exist!");

        // Move to the bottom
        while (node.down != null)
            node = node.down;
        // Because node is on the lowest level so we need remove by down-top
        Node prev = null;
        Node next = null;
        for (; node != null; node = node.up) {
            prev = node.previous;
            next = node.next;
            if (prev != null)
                prev.next = next;
            if (next != null)
                next.previous = prev;
        }

        // Adjust head
        while (head.next == null && head.down != null) {
            head = head.down;
            head.up = null;
        }
        size--;
    }

    public int size() {
        return size;
    }

    protected Node findNode(int value) {
        Node node = head;
        Node next = null;
        Node down = null;

        while (true) {
            // Searching nearest (less than or equal) node with special key
            next = node.next;
            while (next != null && next.value <= value) {
                node = next;
                next = node.next;
            }
            if (node.value != HEAD_VALUE && node.value == value)
                break;
            // Descend to the bottom for continue search
            down = node.down;
            if (down != null) {
                node = down;
            } else {
                break;
            }
        }

        return node;
    }

    protected boolean isBuildLevel() {
        return randomGenerator.nextDoubleFast() < probability;
    }

    protected void horizontalInsert(Node x, Node y) {
        y.previous = x;
        y.next = x.next;
        if (x.next != null)
            x.next.previous = y;
        x.next = y;
    }

    protected void verticalLink(Node x, Node y) {
        x.down = y;
        y.up = x;
    }

    private Node first() {
        Node node = head;
        {
            while (node.down != null)
                node = node.down;

            while (node.previous != null)
                node = node.previous;

            if (node.next != null)
                node = node.next;
        }
        return node;
    }

    private Node last() {
        Node node = head;

        while(node.next != null)
            node = node.next;

        while(node.down != null) {
            while(node.down != null)
                node = node.down;
            while(node.next != null)
                node = node.next;
        }

        return node;
    }

    PrimitiveIterator.OfInt ascending() {
        return new PrimitiveIterator.OfInt() {
            Node node = first();
            int currentValue = 0;

            @Override
            public boolean hasNext() {
                return node != null;
            }

            @Override
            public int nextInt() {
                currentValue = node.value;
                node = node.next;
                return currentValue;
            }
        };
    }

    PrimitiveIterator.OfInt descending() {
        return new PrimitiveIterator.OfInt() {
            Node node = last();
            int currentValue = 0;

            @Override
            public boolean hasNext() {
                return node != null && node.previous != null && node.previous != head;
            }

            @Override
            public int nextInt() {
                currentValue = node.value;
                node = node.previous;
                return currentValue;
            }
        };
    }

    private static class Node {

        private int value;
        private int level;
        private Node up, down, next, previous;

        public Node(int value, int level) {
            this.value = value;
            this.level = level;
        }
    }

    public int firstValue() {
        Node first = first();
        if(first == null)
            throw new NoSuchElementException();

        return first.value;
    }

    public int lastValue() {
        Node last = last();
        if(last == null)
            throw new NoSuchElementException();

        return last.value;
    }
}