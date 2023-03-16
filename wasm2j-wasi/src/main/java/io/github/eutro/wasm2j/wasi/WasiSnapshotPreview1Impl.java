package io.github.eutro.wasm2j.wasi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Signal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static io.github.eutro.wasm2j.wasi.Constants.*;

public abstract class WasiSnapshotPreview1Impl implements WasiSnapshotPreview1 {

    protected abstract ByteBuffer mem();

    protected byte[][] getArgs() {
        return new byte[0][];
    }

    @Override
    public int argsGet(int argv, int argvBuf) {
        ByteBuffer mem = mem();
        ByteBuffer argvSliced = mem.slice().order(ByteOrder.LITTLE_ENDIAN);
        argvSliced.position(argv);
        ByteBuffer bufSliced = mem.slice();
        bufSliced.position(argvBuf);
        for (byte[] arg : getArgs()) {
            argvSliced.putInt(bufSliced.position());
            bufSliced.put(arg).put((byte) 0);
        }
        return WASI_ESUCCESS;
    }

    @Override
    public int argsSizesGet(int argc, int argvBufSize) {
        byte[][] args = getArgs();
        int len = 0;
        for (byte[] arg : args) {
            len += arg.length + 1;
        }
        ByteBuffer mem = mem();
        mem.putInt(argc, args.length);
        mem.putInt(argvBufSize, len);
        return WASI_ESUCCESS;
    }

    @Override
    public int clockResGet(int clockId, int resolution) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int clockTimeGet(int clockId, int precision, int time) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int environGet(int environ, int environBuf) {
        ByteBuffer mem = mem();
        ByteBuffer envSliced = mem.slice().order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer bufSliced = mem.slice();
        bufSliced.position(environBuf);
        for (Map.Entry<String, String> arg : System.getenv().entrySet()) {
            envSliced.putInt(bufSliced.position());
            bufSliced.put(arg.getKey().getBytes(StandardCharsets.UTF_8))
                    .put((byte) '=')
                    .put(arg.getValue().getBytes(StandardCharsets.UTF_8))
                    .put((byte) 0);
        }
        return WASI_ESUCCESS;
    }

    @Override
    public int environSizesGet(int environCount, int environBufSize) {
        Map<String, String> env = System.getenv();
        ByteBuffer mem = mem();
        mem.putInt(environCount, env.size());
        int bufSz = env.entrySet()
                .stream()
                .mapToInt(entry -> entry.getKey().getBytes(StandardCharsets.UTF_8).length
                        + 1 // =
                        + entry.getValue().getBytes(StandardCharsets.UTF_8).length
                        + 1 // \0
                )
                .sum();
        mem.putInt(environBufSize, bufSz);
        return WASI_ESUCCESS;
    }

    static abstract class FdEntry {
        abstract void close() throws IOException;
    }

    interface AllocFdEntry {
        int allocate(long offset, long len) throws IOException;
    }

    interface SeekableFdEntry {
        int seek(ByteBuffer mem, long offset, int whence, int newoffset) throws IOException;
    }

    interface ReadableFdEntry {
        int pread(ByteBuffer mem, int iovs, int iovsLen, long offset, int nread) throws IOException;
    }

    interface WritableFdEntry {
        int pwrite(ByteBuffer mem, int iovs, int iovsLen, long offset, int nwritten) throws IOException;
    }

    interface FlushFdEntry {
        void flush() throws IOException;
    }

    interface DirFdEntry {
        int readdir(ByteBuffer mem, int buf, int bufLen, long cookie, int bufused);

        Path resolve(String pathStr);
    }

    static int readChannel(ReadableByteChannel chan, ByteBuffer mem, int iovs, int iovsLen, int nread) throws IOException {
        ByteBuffer[] bufs = collectBufs(mem, iovs, iovsLen);
        int read;
        if (chan instanceof ScatteringByteChannel) {
            read = (int) ((ScatteringByteChannel) chan).read(bufs);
        } else {
            read = 0;
            for (ByteBuffer buf : bufs) {
                int rem = buf.remaining();
                int readHere = chan.read(buf);
                if (readHere == -1 || readHere < rem) break;
                read += readHere;
            }
        }
        mem.putInt(nread, read);
        return WASI_ESUCCESS;
    }

    static int writeChannel(WritableByteChannel chan, ByteBuffer mem, int iovs, int iovsLen, int nwritten) throws IOException {
        ByteBuffer[] bufs = collectBufs(mem, iovs, iovsLen);
        int written;
        if (chan instanceof GatheringByteChannel) {
            written = (int) ((GatheringByteChannel) chan).write(bufs);
        } else {
            written = 0;
            for (ByteBuffer buf : bufs) {
                int rem = buf.remaining();
                int writtenHere = chan.write(buf);
                written += writtenHere;
                if (writtenHere != rem) break;
            }
        }
        mem.putInt(nwritten, written);
        return WASI_ESUCCESS;
    }

    @NotNull
    private static ByteBuffer[] collectBufs(ByteBuffer mem, int iovs, int iovsLen) {
        ByteBuffer[] bufs = new ByteBuffer[iovsLen];
        int i = 0;
        for (int iov = iovs; iov < iovs + iovsLen * IOV_SIZE; iov += IOV_SIZE) {
            int bufPtr = mem.getInt(iov);
            int bufLen = mem.getInt(iov + I32_SIZE);
            ByteBuffer sliced = mem.slice();
            sliced.position(bufPtr).limit(bufPtr + bufLen);
            bufs[i++] = sliced;
        }
        return bufs;
    }

    static class InStreamFdEntry extends FdEntry implements SeekableFdEntry, ReadableFdEntry {
        private final InputStream in;

        public InStreamFdEntry(InputStream in) {
            this.in = in;
        }

        @Override
        void close() throws IOException {
            in.close();
        }

        @Override
        public int seek(ByteBuffer mem, long offset, int whence, int newoffset) {
            return WASI_ESPIPE;
        }

        @Override
        public int pread(ByteBuffer mem, int iovs, int iovsLen, long offset, int nread) throws IOException {
            // ignored offset
            return readChannel(Channels.newChannel(in), mem, iovs, iovsLen, nread);
        }
    }

    static class OutStreamFdEntry extends FdEntry implements FlushFdEntry, SeekableFdEntry, WritableFdEntry {
        private final OutputStream out;

        OutStreamFdEntry(OutputStream out) {
            this.out = out;
        }

        @Override
        void close() throws IOException {
            out.close();
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public int seek(ByteBuffer mem, long offset, int whence, int newoffset) {
            return WASI_ESPIPE;
        }

        @Override
        public int pwrite(ByteBuffer mem, int iovs, int iovsLen, long offset, int nwritten) throws IOException {
            // ignored offset
            return writeChannel(Channels.newChannel(out), mem, iovs, iovsLen, nwritten);
        }
    }

    static class FileChannelFdEntry extends FdEntry
            implements AllocFdEntry, FlushFdEntry, SeekableFdEntry, ReadableFdEntry, WritableFdEntry {
        private final FileChannel chan;

        FileChannelFdEntry(FileChannel chan) {
            this.chan = chan;
        }

        @Override
        void close() throws IOException {
            chan.close();
        }

        @Override
        public int allocate(long offset, long len) throws IOException {
            long size = chan.size();
            if (Long.compareUnsigned(size, offset + len) < 0) {
                long toExtend = offset + len - size;
                if (toExtend < 0) return WASI_EIO;
                chan.position(size);

                ByteBuffer buf = ByteBuffer.allocate((int) Long.min(toExtend, Integer.MAX_VALUE));
                while (toExtend > 0) {
                    buf.limit((int) Long.min(toExtend, Integer.MAX_VALUE));
                    chan.write(buf);
                    toExtend -= Integer.MAX_VALUE;
                }
            }
            return WASI_ESUCCESS;
        }

        @Override
        public void flush() throws IOException {
            chan.force(true);
        }

        @Override
        public int seek(ByteBuffer mem, long offset, int whence, int newoffset) throws IOException {
            switch (whence) {
                case WASI_WHENCE_SET:
                    if (offset < 0) return WASI_EINVAL;
                    chan.position(offset);
                    break;
                case WASI_WHENCE_CUR: {
                    long newOff;
                    try {
                        newOff = Math.addExact(chan.position(), offset);
                    } catch (ArithmeticException e) {
                        return WASI_EOVERFLOW;
                    }
                    chan.position(newOff);
                    break;
                }
                case WASI_WHENCE_END: {
                    if (offset > 0) return WASI_EINVAL;
                    long newOff = chan.size() + offset;
                    if (newOff < 0) return WASI_EINVAL;
                    chan.position(newOff);
                    break;
                }
                default:
                    return WASI_EINVAL;
            }
            if (newoffset != 0) {
                mem.putLong(newoffset, chan.position());
            }
            return WASI_ESUCCESS;
        }

        @Override
        public int pread(ByteBuffer mem, int iovs, int iovsLen, long offset, int nread) throws IOException {
            if (offset != 0) {
                int err = seek(mem, offset, WASI_WHENCE_SET, 0);
                if (err != WASI_ESUCCESS) return err;
            }
            return readChannel(chan, mem, iovs, iovsLen, nread);
        }

        @Override
        public int pwrite(ByteBuffer mem, int iovs, int iovsLen, long offset, int nwritten) throws IOException {
            if (offset != 0) {
                int err = seek(mem, offset, WASI_WHENCE_SET, 0);
                if (err != WASI_ESUCCESS) return err;
            }
            return writeChannel(chan, mem, iovs, iovsLen, nwritten);
        }
    }

    static class PathFdEntry extends FdEntry implements DirFdEntry {
        private final Path path;

        PathFdEntry(Path path) {
            this.path = path;
        }

        @Override
        void close() {
            // noop
        }

        @Override
        public int readdir(ByteBuffer mem, int buf, int bufLen, long cookie, int bufused) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path resolve(String pathStr) {
            return path.resolve(pathStr);
        }
    }

    private final List<FdEntry> files = new ArrayList<>();
    private final BitSet emptyFiles = new BitSet();

    @Nullable
    private FdEntry fdEntry(int fd) {
        switch (fd) {
            case WASI_STDIN_FILENO:
                return new InStreamFdEntry(System.in);
            case WASI_STDERR_FILENO:
                return new OutStreamFdEntry(System.err);
            case WASI_STDOUT_FILENO:
                return new OutStreamFdEntry(System.out);
        }
        int fdOff = fd - WASI_STDERR_FILENO;
        if (fdOff >= files.size()) return null;
        return files.get(fdOff);
    }

    private int newFd(FdEntry entry) {
        int nsb = emptyFiles.nextSetBit(0);
        int fdOff;
        if (nsb == -1) {
            fdOff = files.size();
            files.add(entry);
        } else {
            files.set(fdOff = nsb, entry);
        }
        return fdOff + WASI_STDERR_FILENO;
    }

    @Override
    public int fdAdvise(int fd, long offset, long len, int advice) {
        return WASI_ESUCCESS;
    }

    @Override
    public int fdAllocate(int fd, long offset, long len) {
        FdEntry entry = fdEntry(fd);
        if (entry == null) return WASI_EBADF;
        if (entry instanceof AllocFdEntry) {
            try {
                return ((AllocFdEntry) entry).allocate(offset, len);
            } catch (IOException e) {
                return WASI_EIO;
            }
        } else {
            return WASI_EBADF;
        }
    }

    @Override
    public int fdClose(int fd) {
        FdEntry entry = fdEntry(fd);
        if (entry == null) return WASI_EBADMSG;
        try {
            entry.close();
        } catch (IOException e) {
            return WASI_EIO;
        }
        if (fd > WASI_STDERR_FILENO) {
            int fdOff = fd - WASI_STDERR_FILENO;
            files.set(fdOff, null);
            emptyFiles.set(fdOff);
        }
        return WASI_ESUCCESS;
    }

    @Override
    public int fdDatasync(int fd) {
        // TODO verify
        FdEntry entry = fdEntry(fd);
        if (!(entry instanceof FlushFdEntry)) return WASI_EBADF;
        try {
            ((FlushFdEntry) entry).flush();
        } catch (IOException e) {
            return WASI_EIO;
        }
        return WASI_ESUCCESS;
    }

    @Override
    public int fdFdstatGet(int fd, int bufPtr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fdFdstatSetFlags(int fd, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fdFdstatSetRights(int fd, int fsRightsBase, int fsRightsInheriting) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fdFilestatGet(int fd, int buf) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fdFilestatSetSize(int fd, int stSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fdFilestatSetTimes(int fd, int stAtim, int stMtim, int fstFlags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fdPread(int fd, int iovs, int iovsLen, long offset, int nread) {
        FdEntry entry = fdEntry(fd);
        if (!(entry instanceof ReadableFdEntry)) return WASI_EBADF;
        try {
            return ((ReadableFdEntry) entry).pread(mem(), iovs, iovsLen, offset, nread);
        } catch (IOException e) {
            return WASI_EIO;
        }
    }

    @Override
    public int fdPrestatDirName(int fd, int path, int pathLen) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fdPrestatGet(int fd, int buf) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fdPwrite(int fd, int iovs, int iovsLen, long offset, int nwritten) {
        FdEntry entry = fdEntry(fd);
        if (!(entry instanceof WritableFdEntry)) return WASI_EBADF;
        try {
            return ((WritableFdEntry) entry).pwrite(mem(), iovs, iovsLen, offset, nwritten);
        } catch (IOException e) {
            return WASI_EIO;
        }
    }

    @Override
    public int fdRead(int fd, int iovs, int iovsLen, int nread) {
        return fdPread(fd, iovs, iovsLen, 0, nread);
    }

    @Override
    public int fdReaddir(int fd, int buf, int bufLen, int cookie, int bufused) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fdRenumber(int from, int to) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fdSeek(int fd, long offset, int whence, int newoffset) {
        FdEntry entry = fdEntry(fd);
        if (!(entry instanceof SeekableFdEntry)) return WASI_EINVAL;
        try {
            return ((SeekableFdEntry) entry).seek(mem(), offset, whence, newoffset);
        } catch (IOException e) {
            return WASI_EIO;
        }
    }

    @Override
    public int fdSync(int fd) {
        // TODO verify
        return fdDatasync(fd);
    }

    @Override
    public int fdTell(int fd, int offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int fdWrite(int fd, int iovs, int iovsLen, int nwritten) {
        return fdPwrite(fd, iovs, iovsLen, 0, nwritten);
    }

    @Override
    public int pathCreateDirectory(int fd, int path, int pathLen) {
        // TODO verify
        FdEntry entry = fdEntry(fd);
        if (!(entry instanceof DirFdEntry)) return WASI_EBADF;
        String pathStr = getString(path, pathLen);

        Path newPath = ((DirFdEntry) entry).resolve(pathStr);
        try {
            Files.createDirectories(newPath);
        } catch (IOException e) {
            return WASI_EIO;
        }
        return WASI_ESUCCESS;
    }

    @NotNull
    private String getString(int ptr, int len) {
        ByteBuffer sliced = mem().slice();
        sliced.position(ptr).limit(ptr + len);
        return StandardCharsets.UTF_8.decode(sliced).toString();
    }

    @Override
    public int pathFilestatGet(int fd, int flags, int path, int pathLen, int buf) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int pathFilestatSetTimes(int fd, int flags, int path, int pathLen, int stAtim, int stMtim, int fstFlags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int pathLink(int oldFd, int oldFlags, int oldPath, int oldPathLen, int newFd, int newPath, int newPathLen) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int pathOpen(int dirfd,
                        int dirflags,
                        int path,
                        int pathLen,
                        int oFlags,
                        int fsRightsBase,
                        int fsRightsInheriting,
                        int fsFlags,
                        int fd) {
        // TODO verify
        FdEntry entry = fdEntry(dirfd);
        if (!(entry instanceof DirFdEntry)) return WASI_EBADF;
        String pathStr = getString(path, pathLen);
        Path newPath = ((DirFdEntry) entry).resolve(pathStr);
        ByteBuffer mem = mem();
        mem.getInt(fd); // bounds check
        int fdVal;
        try {
            fdVal = newFd(new FileChannelFdEntry(FileChannel.open(newPath)));
        } catch (IOException e) {
            return WASI_EIO;
        }
        mem.putInt(fd, fdVal);
        return WASI_ESUCCESS;
    }

    @Override
    public int pathReadlink(int dirFd, int path, int pathLen, int buf, int bufLen, int bufUsed) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int pathRemoveDirectory(int fd, int path, int pathLen) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int pathRename(int oldFd, int oldPath, int oldPathLen, int newFd, int newPath, int newPathLen) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int pathSymlink(int oldPath, int oldPathLen, int fd, int newPath, int newPathLen) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int pathUnlinkFile(int fd, int path, int pathLen) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int pollOneoff(int in, int out, int nsubscriptions, int nevents) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int procExit(int code) {
        System.exit(code);
        return WASI_ESUCCESS;
    }

    @Override
    public int procRaise(int sig) {
        // TODO test
        String nm;
        switch (sig) {
            // @formatter:off
            case WASI_SIGHUP: nm = "HUP"; break;
            case WASI_SIGINT: nm = "INT"; break;
            case WASI_SIGQUIT: nm = "QUIT"; break;
            case WASI_SIGILL: nm = "ILL"; break;
            case WASI_SIGTRAP: nm = "TRAP"; break;
            case WASI_SIGABRT: nm = "ABRT"; break;
            case WASI_SIGBUS: nm = "BUS"; break;
            case WASI_SIGFPE: nm = "FPE"; break;
            case WASI_SIGKILL: nm = "KILL"; break;
            case WASI_SIGUSR1: nm = "USR1"; break;
            case WASI_SIGSEGV: nm = "SEGV"; break;
            case WASI_SIGUSR2: nm = "USR2"; break;
            case WASI_SIGPIPE: nm = "PIPE"; break;
            case WASI_SIGALRM: nm = "ALRM"; break;
            case WASI_SIGTERM: nm = "TERM"; break;
            case WASI_SIGCHLD: nm = "CHLD"; break;
            case WASI_SIGCONT: nm = "CONT"; break;
            case WASI_SIGSTOP: nm = "STOP"; break;
            case WASI_SIGTSTP: nm = "TSTP"; break;
            case WASI_SIGTTIN: nm = "TTIN"; break;
            case WASI_SIGTTOU: nm = "TTOU"; break;
            case WASI_SIGURG: nm = "URG"; break;
            case WASI_SIGXCPU: nm = "XCPU"; break;
            case WASI_SIGXFSZ: nm = "XFSZ"; break;
            case WASI_SIGVTALRM: nm = "VTALRM"; break;
            case WASI_SIGPROF: nm = "PROF"; break;
            case WASI_SIGWINCH: nm = "WINCH"; break;
            case WASI_SIGPOLL: nm = "POLL"; break;
            case WASI_SIGPWR: nm = "PWR"; break;
            case WASI_SIGSYS: nm = "SYS"; break;
            // @formatter:on
            default:
                return WASI_EINVAL;
        }
        Signal.raise(new Signal(nm));
        return WASI_ESUCCESS;
    }

    @Override
    public int randomGet(int buf, int bufLen) {
        SecureRandom sr = new SecureRandom();
        byte[] bytes = new byte[bufLen];
        sr.nextBytes(bytes);
        ByteBuffer mem = mem().slice();
        mem.position(buf);
        mem.put(bytes);
        return WASI_ESUCCESS;
    }

    @Override
    public int schedYield() {
        Thread.yield();
        return WASI_ESUCCESS;
    }

    @Override
    public int sockAccept(int fd, int flags, int sock) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int sockRecv(int sock, int riData, int riDataLen, int riFlags, int roDatalen, int roFlags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int sockSend(int sock, int siData, int siDataLen, int siFlags, int soDatalen) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int sockShutdown(int sock, int how) {
        throw new UnsupportedOperationException();
    }
}
