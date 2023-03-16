package io.github.eutro.wasm2j.wasi;

public class Constants {
    public static final byte WASI_ADVICE_NORMAL = 0;
    public static final byte WASI_ADVICE_SEQUENTIAL = 1;
    public static final byte WASI_ADVICE_RANDOM = 2;
    public static final byte WASI_ADVICE_WILLNEED = 3;
    public static final byte WASI_ADVICE_DONTNEED = 4;
    public static final byte WASI_ADVICE_NOREUSE = 5;

    public static final int WASI_CLOCK_REALTIME = 0;
    public static final int WASI_CLOCK_MONOTONIC = 1;
    public static final int WASI_CLOCK_PROCESS_CPUTIME_ID = 2;
    public static final int WASI_CLOCK_THREAD_CPUTIME_ID = 3;

    public static final long WASI_DIRCOOKIE_START = 0;

    public static final short WASI_ESUCCESS = 0;
    public static final short WASI_E2BIG = 1;
    public static final short WASI_EACCES = 2;
    public static final short WASI_EADDRINUSE = 3;
    public static final short WASI_EADDRNOTAVAIL = 4;
    public static final short WASI_EAFNOSUPPORT = 5;
    public static final short WASI_EAGAIN = 6;
    public static final short WASI_EALREADY = 7;
    public static final short WASI_EBADF = 8;
    public static final short WASI_EBADMSG = 9;
    public static final short WASI_EBUSY = 10;
    public static final short WASI_ECANCELED = 11;
    public static final short WASI_ECHILD = 12;
    public static final short WASI_ECONNABORTED = 13;
    public static final short WASI_ECONNREFUSED = 14;
    public static final short WASI_ECONNRESET = 15;
    public static final short WASI_EDEADLK = 16;
    public static final short WASI_EDESTADDRREQ = 17;
    public static final short WASI_EDOM = 18;
    public static final short WASI_EDQUOT = 19;
    public static final short WASI_EEXIST = 20;
    public static final short WASI_EFAULT = 21;
    public static final short WASI_EFBIG = 22;
    public static final short WASI_EHOSTUNREACH = 23;
    public static final short WASI_EIDRM = 24;
    public static final short WASI_EILSEQ = 25;
    public static final short WASI_EINPROGRESS = 26;
    public static final short WASI_EINTR = 27;
    public static final short WASI_EINVAL = 28;
    public static final short WASI_EIO = 29;
    public static final short WASI_EISCONN = 30;
    public static final short WASI_EISDIR = 31;
    public static final short WASI_ELOOP = 32;
    public static final short WASI_EMFILE = 33;
    public static final short WASI_EMLINK = 34;
    public static final short WASI_EMSGSIZE = 35;
    public static final short WASI_EMULTIHOP = 36;
    public static final short WASI_ENAMETOOLONG = 37;
    public static final short WASI_ENETDOWN = 38;
    public static final short WASI_ENETRESET = 39;
    public static final short WASI_ENETUNREACH = 40;
    public static final short WASI_ENFILE = 41;
    public static final short WASI_ENOBUFS = 42;
    public static final short WASI_ENODEV = 43;
    public static final short WASI_ENOENT = 44;
    public static final short WASI_ENOEXEC = 45;
    public static final short WASI_ENOLCK = 46;
    public static final short WASI_ENOLINK = 47;
    public static final short WASI_ENOMEM = 48;
    public static final short WASI_ENOMSG = 49;
    public static final short WASI_ENOPROTOOPT = 50;
    public static final short WASI_ENOSPC = 51;
    public static final short WASI_ENOSYS = 52;
    public static final short WASI_ENOTCONN = 53;
    public static final short WASI_ENOTDIR = 54;
    public static final short WASI_ENOTEMPTY = 55;
    public static final short WASI_ENOTRECOVERABLE = 56;
    public static final short WASI_ENOTSOCK = 57;
    public static final short WASI_ENOTSUP = 58;
    public static final short WASI_ENOTTY = 59;
    public static final short WASI_ENXIO = 60;
    public static final short WASI_EOVERFLOW = 61;
    public static final short WASI_EOWNERDEAD = 62;
    public static final short WASI_EPERM = 63;
    public static final short WASI_EPIPE = 64;
    public static final short WASI_EPROTO = 65;
    public static final short WASI_EPROTONOSUPPORT = 66;
    public static final short WASI_EPROTOTYPE = 67;
    public static final short WASI_ERANGE = 68;
    public static final short WASI_EROFS = 69;
    public static final short WASI_ESPIPE = 70;
    public static final short WASI_ESRCH = 71;
    public static final short WASI_ESTALE = 72;
    public static final short WASI_ETIMEDOUT = 73;
    public static final short WASI_ETXTBSY = 74;
    public static final short WASI_EXDEV = 75;
    public static final short WASI_ENOTCAPABLE = 76;

    public static final short WASI_EVENT_FD_READWRITE_HANGUP = 1;

    public static final byte WASI_EVENTTYPE_CLOCK = 0;
    public static final byte WASI_EVENTTYPE_FD_READ = 1;
    public static final byte WASI_EVENTTYPE_FD_WRITE = 2;

    public static final int WASI_STDIN_FILENO = 0;
    public static final int WASI_STDOUT_FILENO = 1;
    public static final int WASI_STDERR_FILENO = 2;

    public static final short WASI_FDFLAG_APPEND = 1;
    public static final short WASI_FDFLAG_DSYNC = 1 << 1;
    public static final short WASI_FDFLAG_NONBLOCK = 1 << 2;
    public static final short WASI_FDFLAG_RSYNC = 1 << 3;
    public static final short WASI_FDFLAG_SYNC = 1 << 4;

    public static final byte WASI_PREOPENTYPE_DIR = 0;

    public static final byte WASI_FILETYPE_UNKNOWN = 0;
    public static final byte WASI_FILETYPE_BLOCK_DEVICE = 1;
    public static final byte WASI_FILETYPE_CHARACTER_DEVICE = 2;
    public static final byte WASI_FILETYPE_DIRECTORY = 3;
    public static final byte WASI_FILETYPE_REGULAR_FILE = 4;
    public static final byte WASI_FILETYPE_SOCKET_DGRAM = 5;
    public static final byte WASI_FILETYPE_SOCKET_STREAM = 6;
    public static final byte WASI_FILETYPE_SYMBOLIC_LINK = 7;

    public static final short WASI_FILESTAT_SET_ATIM = 1;
    public static final short WASI_FILESTAT_SET_ATIM_NOW = 1 << 1;
    public static final short WASI_FILESTAT_SET_MTIM = 1 << 2;
    public static final short WASI_FILESTAT_SET_MTIM_NOW = 1 << 3;

    public static final int WASI_LOOKUP_SYMLINK_FOLLOW = 1;

    public static final short WASI_O_CREAT = 1;
    public static final short WASI_O_DIRECTORY = 1 << 1;
    public static final short WASI_O_EXCL = 1 << 2;
    public static final short WASI_O_TRUNC = 1 << 3;

    public static final short WASI_SOCK_RECV_PEEK = 1;
    public static final short WASI_SOCK_RECV_WAITALL = 1 << 1;

    public static final long WASI_RIGHT_FD_DATASYNC = 1;
    public static final long WASI_RIGHT_FD_READ = 1 << 1;
    public static final long WASI_RIGHT_FD_SEEK = 1 << 2;
    public static final long WASI_RIGHT_FD_FDSTAT_SET_FLAGS = 1 << 3;
    public static final long WASI_RIGHT_FD_SYNC = 1 << 4;
    public static final long WASI_RIGHT_FD_TELL = 1 << 5;
    public static final long WASI_RIGHT_FD_WRITE = 1 << 6;
    public static final long WASI_RIGHT_FD_ADVISE = 1 << 7;
    public static final long WASI_RIGHT_FD_ALLOCATE = 1 << 8;
    public static final long WASI_RIGHT_PATH_CREATE_DIRECTORY = 1 << 9;
    public static final long WASI_RIGHT_PATH_CREATE_FILE = 1 << 10;
    public static final long WASI_RIGHT_PATH_LINK_SOURCE = 1 << 11;
    public static final long WASI_RIGHT_PATH_LINK_TARGET = 1 << 12;
    public static final long WASI_RIGHT_PATH_OPEN = 1 << 13;
    public static final long WASI_RIGHT_FD_READDIR = 1 << 14;
    public static final long WASI_RIGHT_PATH_READLINK = 1 << 15;
    public static final long WASI_RIGHT_PATH_RENAME_SOURCE = 1 << 16;
    public static final long WASI_RIGHT_PATH_RENAME_TARGET = 1 << 17;
    public static final long WASI_RIGHT_PATH_FILESTAT_GET = 1 << 18;
    public static final long WASI_RIGHT_PATH_FILESTAT_SET_SIZE = 1 << 19;
    public static final long WASI_RIGHT_PATH_FILESTAT_SET_TIMES = 1 << 20;
    public static final long WASI_RIGHT_FD_FILESTAT_GET = 1 << 21;
    public static final long WASI_RIGHT_FD_FILESTAT_SET_SIZE = 1 << 22;
    public static final long WASI_RIGHT_FD_FILESTAT_SET_TIMES = 1 << 23;
    public static final long WASI_RIGHT_PATH_SYMLINK = 1 << 24;
    public static final long WASI_RIGHT_PATH_REMOVE_DIRECTORY = 1 << 25;
    public static final long WASI_RIGHT_PATH_UNLINK_FILE = 1 << 26;
    public static final long WASI_RIGHT_POLL_FD_READWRITE = 1 << 27;
    public static final long WASI_RIGHT_SOCK_SHUTDOWN = 1 << 28;

    public static final short WASI_SOCK_RECV_DATA_TRUNCATED = 1;

    public static final byte WASI_SHUT_RD = 1;
    public static final byte WASI_SHUT_WR = 1 << 1;


    public static final byte WASI_SIGHUP = 1;
    public static final byte WASI_SIGINT = 2;
    public static final byte WASI_SIGQUIT = 3;
    public static final byte WASI_SIGILL = 4;
    public static final byte WASI_SIGTRAP = 5;
    public static final byte WASI_SIGABRT = 6;
    public static final byte WASI_SIGBUS = 7;
    public static final byte WASI_SIGFPE = 8;
    public static final byte WASI_SIGKILL = 9;
    public static final byte WASI_SIGUSR1 = 10;
    public static final byte WASI_SIGSEGV = 11;
    public static final byte WASI_SIGUSR2 = 12;
    public static final byte WASI_SIGPIPE = 13;
    public static final byte WASI_SIGALRM = 14;
    public static final byte WASI_SIGTERM = 15;
    public static final byte WASI_SIGCHLD = 16;
    public static final byte WASI_SIGCONT = 17;
    public static final byte WASI_SIGSTOP = 18;
    public static final byte WASI_SIGTSTP = 19;
    public static final byte WASI_SIGTTIN = 20;
    public static final byte WASI_SIGTTOU = 21;
    public static final byte WASI_SIGURG = 22;
    public static final byte WASI_SIGXCPU = 23;
    public static final byte WASI_SIGXFSZ = 24;
    public static final byte WASI_SIGVTALRM = 25;
    public static final byte WASI_SIGPROF = 26;
    public static final byte WASI_SIGWINCH = 27;
    public static final byte WASI_SIGPOLL = 28;
    public static final byte WASI_SIGPWR = 29;
    public static final byte WASI_SIGSYS = 30;

    public static final byte WASI_WHENCE_SET = 0;
    public static final byte WASI_WHENCE_CUR = 1;
    public static final byte WASI_WHENCE_END = 2;
}
