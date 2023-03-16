package io.github.eutro.wasm2j.wasi;

@SuppressWarnings("unused") // linked by codegen
public interface WasiSnapshotPreview1 {
    int I32_SIZE = 4;
    int I64_SIZE = 8;
    int IOV_SIZE = I32_SIZE + I32_SIZE;

    /**
     * Read command-line argument data. The size of the array should match that returned by args_sizes_get.
     * Each argument is expected to be \0 terminated.
     */
    int argsGet(int argv, int argvBuf);

    /**
     * Return command-line argument data sizes.
     */
    int argsSizesGet(int argc, int argvBufSize);

    /**
     * Return the resolution of a clock. Implementations are required to provide a non-zero value for supported clocks.
     * For unsupported clocks, return errno::inval. Note: This is similar to clock_getres in POSIX.
     */
    int clockResGet(int clockId, int resolution);

    /**
     * Return the time value of a clock. Note: This is similar to clock_gettime in POSIX.
     */
    int clockTimeGet(int clockId, int precision, int time);

    /**
     * Read environment variable data. The sizes of the buffers should match that returned by environ_sizes_get.
     * Key/value pairs are expected to be joined with =s, and terminated with \0s.
     */
    int environGet(int environ, int environBuf);

    /**
     * Return environment variable data sizes.
     */
    int environSizesGet(int environCount, int environBufSize);

    /**
     * Provide file advisory information on a file descriptor.
     * Note: This is similar to posix_fadvise in POSIX.
     */
    int fdAdvise(int fd, long offset, long len, int advice);

    /**
     * Force the allocation of space in a file.
     * Note: This is similar to posix_fallocate in POSIX.
     */
    int fdAllocate(int fd, long offset, long len);

    /**
     * Close a file descriptor. Note: This is similar to close in POSIX.
     */
    int fdClose(int fd);

    /**
     * Synchronize the data of a file to disk.
     * Note: This is similar to fdatasync in POSIX.
     */
    int fdDatasync(int fd);

    /**
     * Get the attributes of a file descriptor.
     * Note: This returns similar flags to fsync(fd, F_GETFL) in POSIX, as well as additional fields.
     */
    int fdFdstatGet(int fd, int bufPtr);

    /**
     * Adjust the flags associated with a file descriptor.
     * Note: This is similar to fcntl(fd, F_SETFL, flags) in POSIX.
     */
    int fdFdstatSetFlags(int fd, int flags);

    /**
     * Adjust the rights associated with a file descriptor.
     * This can only be used to remove rights, and returns errno::notcapable
     * if called in a way that would attempt to add rights
     */
    int fdFdstatSetRights(int fd, int fsRightsBase, int fsRightsInheriting);

    /**
     * Return the attributes of an open file.
     */
    int fdFilestatGet(int fd, int buf);

    /**
     * Adjust the size of an open file. If this increases the file's size, the extra bytes are filled with zeros.
     * Note: This is similar to ftruncate in POSIX.
     */
    int fdFilestatSetSize(int fd, int stSize);

    /**
     * Adjust the timestamps of an open file or directory. Note: This is similar to futimens in POSIX.
     */
    int fdFilestatSetTimes(int fd, int stAtim, int stMtim, int fstFlags);

    /**
     * Read from a file descriptor, without using and updating the file descriptor's offset.
     * Note: This is similar to preadv in POSIX.
     */
    int fdPread(int fd, int iovs, int iovsLen, long offset, int nread);

    /**
     * Return a description of the given preopened file descriptor.
     */
    int fdPrestatDirName(int fd, int path, int pathLen);

    /**
     * Return a description of the given preopened file descriptor.
     */
    int fdPrestatGet(int fd, int buf);

    /**
     * Write to a file descriptor, without using and updating the file descriptor's offset.
     * Note: This is similar to pwritev in POSIX.
     */
    int fdPwrite(int fd, int iovs, int iovsLen, long offset, int nwritten);

    /**
     * Read from a file descriptor. Note: This is similar to readv in POSIX.
     */
    int fdRead(int fd, int iovs, int iovsLen, int nread);

    /**
     * Read directory entries from a directory.
     * When successful, the contents of the output buffer consist of a sequence of directory entries.
     * Each directory entry consists of a dirent object, followed by dirent::d_namlen bytes holding
     * the name of the directory entry. This function fills the output buffer as much as possible,
     * potentially truncating the last directory entry. This allows the caller to grow its read buffer size
     * in case it's too small to fit a single large directory entry, or skip the oversized directory entry.
     */
    int fdReaddir(int fd, int buf, int bufLen, int cookie, int bufused);

    /**
     * Atomically replace a file descriptor by renumbering another file descriptor.
     * Due to the strong focus on thread safety, this environment does not provide a mechanism to duplicate or renumber a file descriptor to an arbitrary number, like dup2(). This would be prone to race conditions, as an actual file descriptor with the same number could be allocated by a different thread at the same time. This function provides a way to atomically renumber file descriptors, which would disappear if dup2() were to be removed entirely.
     */
    int fdRenumber(int from, int to);

    /**
     * Move the offset of a file descriptor. Note: This is similar to lseek in POSIX.
     */
    int fdSeek(int fd, long offset, int whence, int newoffset);

    /**
     * Synchronize the data and metadata of a file to disk. Note: This is similar to fsync in POSIX.
     */
    int fdSync(int fd);

    /**
     * Return the current offset of a file descriptor. Note: This is similar to lseek(fd, 0, SEEK_CUR) in POSIX.
     */
    int fdTell(int fd, int offset);

    /**
     * Write to a file descriptor. Note: This is similar to writev in POSIX.
     */
    int fdWrite(int fd, int iovs, int iovsLen, int nwritten);

    /**
     * Create a directory. Note: This is similar to mkdirat in POSIX.
     */
    int pathCreateDirectory(int fd, int path, int pathLen);

    /**
     * Return the attributes of a file or directory. Note: This is similar to stat in POSIX.
     */
    int pathFilestatGet(int fd, int flags, int path, int pathLen, int buf);

    /**
     * Adjust the timestamps of a file or directory. Note: This is similar to utimensat in POSIX.
     */
    int pathFilestatSetTimes(int fd, int flags, int path, int pathLen, int stAtim, int stMtim, int fstFlags);

    /**
     * Create a hard link. Note: This is similar to linkat in POSIX.
     */
    int pathLink(int oldFd, int oldFlags, int oldPath, int oldPathLen, int newFd, int newPath, int newPathLen);

    /**
     * Open a file or directory. The returned file descriptor is not guaranteed to be the lowest-numbered file
     * descriptor not currently open; it is randomized to prevent applications from depending on making assumptions
     * about indexes, since this is error-prone in multi-threaded contexts. The returned file descriptor is guaranteed
     * to be less than 2**31. Note: This is similar to openat in POSIX.
     */
    int pathOpen(int dirfd,
                 int dirflags,
                 int path,
                 int pathLen,
                 int oFlags,
                 int fsRightsBase,
                 int fsRightsInheriting,
                 int fsFlags,
                 int fd);

    /**
     * Read the contents of a symbolic link. Note: This is similar to readlinkat in POSIX.
     */
    int pathReadlink(int dirFd, int path, int pathLen, int buf, int bufLen, int bufUsed);

    /**
     * Remove a directory. Return errno::notempty if the directory is not empty. Note: This is similar to
     * unlinkat(fd, path, AT_REMOVEDIR) in POSIX.
     */
    int pathRemoveDirectory(int fd, int path, int pathLen);

    /**
     * Rename a file or directory. Note: This is similar to renameat in POSIX.
     */
    int pathRename(int oldFd, int oldPath, int oldPathLen, int newFd, int newPath, int newPathLen);

    /**
     * Create a symbolic link. Note: This is similar to symlinkat in POSIX.
     */
    int pathSymlink(int oldPath, int oldPathLen, int fd, int newPath, int newPathLen);

    /**
     * Unlink a file. Return errno::isdir if the path refers to a directory. Note: This is similar to
     * unlinkat(fd, path, 0) in POSIX.
     */
    int pathUnlinkFile(int fd, int path, int pathLen);

    /**
     * Concurrently poll for the occurrence of a set of events.
     */
    int pollOneoff(int in, int out, int nsubscriptions, int nevents);

    /**
     * Terminate the process normally. An exit code of 0 indicates successful termination of the program.
     * The meanings of other values is dependent on the environment.
     */
    int procExit(int code);

    /**
     * Send a signal to the process of the calling thread. Note: This is similar to raise in POSIX.
     */
    int procRaise(int sig);

    /**
     * Write high-quality random data into a buffer. This function blocks when the implementation is unable to
     * immediately provide sufficient high-quality random data. This function may execute slowly, so when large
     * mounts of random data are required, it's advisable to use this function to seed a pseudo-random number
     * generator, rather than to provide the random data directly.
     */
    int randomGet(int buf, int bufLen);

    /**
     * Temporarily yield execution of the calling thread. Note: This is similar to sched_yield in POSIX.
     */
    int schedYield();

    /**
     * Accept a new incoming connection. Note: This is similar to accept in POSIX.
     */
    int sockAccept(int fd, int flags, int sock);

    /**
     * Receive a message from a socket. Note: This is similar to recv in POSIX, though it also supports reading the
     * data into multiple buffers in the manner of readv.
     */
    int sockRecv(int sock, int riData, int riDataLen, int riFlags, int roDatalen, int roFlags);

    /**
     * Send a message on a socket. Note: This is similar to send in POSIX, though it also supports writing the data
     * from multiple buffers in the manner of writev.
     */
    int sockSend(int sock, int siData, int siDataLen, int siFlags, int soDatalen);

    /**
     * Shut down socket send and receive channels. Note: This is similar to shutdown in POSIX.
     */
    int sockShutdown(int sock, int how);
}
