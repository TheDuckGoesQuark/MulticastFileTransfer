package chord.unicastpiped.node;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.*;

/**
 * Each node stores a map of which node it has,
 * and the offset of the corresponding block in the file being sent
 **/
public class BlockStore {

    private ConcurrentHashMap<Integer, BlockingQueue<Long>> blockToOffset = new ConcurrentHashMap<>();
    private RandomAccessFile randomAccessFile;
    private File file;
    private int blocksReceived;
    public final Object lock = new Object();

    BlockStore() { }

    public BlockStore(RandomAccessFile randomAccessFile, long fileLength, boolean isRoot, File file) throws IOException {
        this.randomAccessFile = randomAccessFile;
        this.file = file;
        randomAccessFile.setLength(fileLength);
        if (isRoot) {
            long offset = 0;
            int blockNumber = 0;
            while (offset < fileLength) {
                BlockingQueue<Long> queue = ensureQueueExists(blockNumber);
                queue.add(offset);
                blockToOffset.put(blockNumber, queue);
                blockNumber++;
                offset += NodeUtil.FILE_BUFFER_SIZE;
            }
        }
    }

    private synchronized BlockingQueue<Long> ensureQueueExists(int key) {
        if (blockToOffset.containsKey(key)) {
            return blockToOffset.get(key);
        } else {
            BlockingQueue<Long> queue = new ArrayBlockingQueue<>(1);
            blockToOffset.put(key, queue);
            return queue;
        }
    }

    public void getBlock(int blockNumber, byte[] buffer) throws InterruptedException {
        BlockingQueue<Long> queue = ensureQueueExists(blockNumber);
        try {
            long offset = queue.poll(60L, TimeUnit.SECONDS);
            queue.add(offset); // Put offset back into queue. Since get will only be called by one thread, this does not result in a race condition
            randomAccessFile.seek(offset);
            randomAccessFile.readFully(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeBlock(int blockNumber, byte[] buffer) throws IOException {
        BlockingQueue<Long> queue = ensureQueueExists(blockNumber);
        long offset = blockNumber * NodeUtil.FILE_BUFFER_SIZE;
        randomAccessFile.seek(offset);
        randomAccessFile.write(buffer);
        queue.add(offset);
        blockToOffset.put(blockNumber, queue);
        blocksReceived++;
    }

    public boolean allFilesReceived() throws IOException {
        return getExpectedNumberOfBlocks() == blocksReceived;
    }

    public int getExpectedNumberOfBlocks() {
        return blockToOffset.size();
    }

    public long getFileLength() throws IOException {
        return randomAccessFile.length();
    }

    public String getFileName() {
        return file.getName();
    }

    public boolean isInitialised() {
        return randomAccessFile != null;
    }


}
