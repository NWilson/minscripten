"use strict";

// TODO generate these automatically?
const MINSCRIPTEN_RELEASE = "1.0";
const MINSCRIPTEN_BUILD = "1";

//
// Helpers
//

class ExitException extends Error {
  constructor(status) {
    super();
    this.status = status;
  }
  toString() {
    return "module called exit(" + this.status + ")";
  }
}

class SignalException extends Error {
  constructor(signal) {
    super();
    this.signal = signal;
  }
  toString() {
    return "module received signal " + this.signal;
  }
}

const sizeofInt = 4,
    sizeofLong = 4,
    sizeofLonglong = 8;
const sizeofUint = sizeofInt,
    sizeofUlong = sizeofLong,
    sizeofUlonglong = sizeofLonglong;
const NULL = 0;

// From sys/types.h:
const sizeofClockt = sizeofLong;

// From errno.h:
const EPERM = 1, ENOENT = 2, ESRCH = 3, EINTR = 4, EIO = 5, ENXIO = 6,
    E2BIG = 7, ENOEXEC = 8, EBADF = 9, ECHILD = 10, EAGAIN = 11, ENOMEM = 12,
    EACCES = 13, EFAULT = 14, ENOTBLK = 15, EBUSY = 16, EEXIST = 17, EXDEV = 18,
    ENODEV = 19, ENOTDIR = 20, EISDIR = 21, EINVAL = 22, ENFILE = 23,
    EMFILE = 24, ENOTTY = 25, ETXTBSY = 26, EFBIG = 27, ENOSPC = 28,
    ESPIPE = 29, EROFS = 30, EMLINK = 31, EPIPE = 32, EDOM = 33, ERANGE = 34,
    EDEADLK = 35, ENAMETOOLONG = 36, ENOLCK = 37, ENOSYS = 38, ENOTEMPTY = 39,
    ELOOP = 40, ENOMSG = 42, EIDRM = 43, ECHRNG = 44, EL2NSYNC = 45,
    EL3HLT = 46, EL3RST = 47, ELNRNG = 48, EUNATCH = 49, ENOCSI = 50,
    EL2HLT = 51, EBADE = 52, EBADR = 53, EXFULL = 54, ENOANO = 55, EBADRQC = 56,
    EBADSLT = 57, EBFONT = 59, ENOSTR = 60, ENODATA = 61, ETIME = 62,
    ENOSR = 63, ENONET = 64, ENOPKG = 65, EREMOTE = 66, ENOLINK = 67, EADV = 68,
    ESRMNT = 69, ECOMM = 70, EPROTO = 71, EMULTIHOP = 72,EDOTDOT = 73,
    EBADMSG = 74, EOVERFLOW = 75, ENOTUNIQ = 76, EBADFD = 77, EREMCHG = 78,
    ELIBACC = 79, ELIBBAD = 80, ELIBSCN = 81, ELIBMAX = 82, ELIBEXEC = 83,
    EILSEQ = 84, ERESTART = 85, ESTRPIPE = 86, EUSERS = 87, ENOTSOCK = 88,
    EDESTADDRREQ = 89, EMSGSIZE = 90, EPROTOTYPE = 91, ENOPROTOOPT = 92,
    EPROTONOSUPPORT = 93, ESOCKTNOSUPPORT = 94, EOPNOTSUPP = 95,
    EPFNOSUPPORT = 96, EAFNOSUPPORT = 97, EADDRINUSE = 98, EADDRNOTAVAIL = 99,
    ENETDOWN = 100, ENETUNREACH = 101, ENETRESET = 102, ECONNABORTED = 103,
    ECONNRESET = 104, ENOBUFS = 105, EISCONN = 106, ENOTCONN = 107,
    ESHUTDOWN = 108, ETOOMANYREFS = 109, ETIMEDOUT = 110, ECONNREFUSED = 111,
    EHOSTDOWN = 112, EHOSTUNREACH = 113, EALREADY = 114, EINPROGRESS = 115,
    ESTALE = 116, EUCLEAN = 117, ENOTNAM = 118, ENAVAIL = 119, EISNAM = 120,
    EREMOTEIO = 121, EDQUOT = 122, ENOMEDIUM = 123, EMEDIUMTYPE = 124,
    ECANCELED = 125, ENOKEY = 126, EKEYEXPIRED = 127, EKEYREVOKED = 128,
    EKEYREJECTED = 129, EOWNERDEAD = 130, ENOTRECOVERABLE = 131, ERFKILL = 132,
    EHWPOISON = 133;
const EWOULDBLOCK = EAGAIN, EDEADLOCK = EDEADLK, ENOTSUP = EOPNOTSUPP;

// From fcntl.h:
const O_CLOEXEC = 0o2000000;

// From sys/personality.h:
const PER_LINUX = 0;

// From sys/time.h:
const sizeofTimet = sizeofLonglong,
    sizeofSusecondst = sizeofLonglong;
const sizeofTimeval = sizeofTimet + sizeofSusecondst;
const CLOCK_REALTIME = 0,
    CLOCK_MONOTONIC = 1;

// From sys/random.h:
const GRND_NONBLOCK = 0x1,
    GRND_RANDOM = 0x2;

// From sys/timex.h:
const ADJ_OFFSET_SS_READ = 0xa001;
const TIME_OK = 0;
const sizeofTimex = 4 * sizeofInt + 15 * sizeofLong + sizeofTimeval + 11;

// From sys/resource.h:
const PRIO_PROCESS = 0, PRIO_PGRP = 1, PRIO_USER = 2;
const RUSAGE_SELF = 0, RUSAGE_THREAD = 1, RUSAGE_CHILDREN = -1;
const sizeofRusage = 2 * sizeofTimeval + 30 * sizeofLong;

import { __linear_memory as linearMem } from "__symbols";

const memory = new class {
  constructor() {
    this.u8 = null;
    this.u32 = null;
    this.s32 = null;

    const tester = new Uint32Array(1);
    tester[0] = 0x01;
    if (new Uint8Array(tester.buffer)[0] !== 0x01)
      throw new Error("Unsupported platform, TypedArray must be little-endian");
  }
  getU8() {
    let u8 = this.u8;
    if (u8 !== null && u8.buffer == linearMem.buffer)
      return u8;
    return (this.u8 = new Uint8Array(linearMem.buffer));
  }
  getU32() {
    let u32 = this.u32;
    if (u32 !== null && u32.buffer == linearMem.buffer)
      return u32;
    return (this.u32 = new Uint32Array(linearMem.buffer));
  }
  getS32() {
    let s32 = this.s32;
    if (s32 !== null && s32.buffer == linearMem.buffer)
      return s32;
    return (this.s32 = new Int32Array(linearMem.buffer));
  }
  getUsage() {
    return linearMem.buffer.byteLength;
  }
};

function writeU32(address, value) {
  address = address >>> 0;
  value = value >>> 0;
  const u32 = memory.getU32();
  if ((address & 0x3) !== 0 || (address >>= 2) >= a.length)
    return false;
  u32[address] = value;
  return true;
}
function writeS32(address, value) {
  address = address >>> 0;
  value = value | 0;
  const s32 = memory.getS32();
  if ((address & 0x3) !== 0 || (address >>= 2) >= a.length)
    return false;
  s32[address] = value;
  return true;
}
function writeS64(address, value) {
  // XXX return false on failure
  return false;
}
function writeString(address, str, maxLen) {
  // XXX return false on failure
  return false;
}
function writeMem(address, value, len) {
  // XXX return false on failure
  return false;
}
const writeUint = writeU32,
    writeUlong = writeU32,
    writeLong = writeS32,
    writeLonglong = writeS64;
const writeGidt = writeUint,
    writeUidt = writeUint;
const writeClockt = writeLong,
    writeTimet = writeLonglong,
    writeSusecondst = writeLonglong;

function readU32(address) {
  address = address >>> 0;
  const u32 = memory.getU32();
  if ((address & 0x3) !== 0 || (address >>= 2) >= a.length)
    return [0, false];
  return [u32[address], true];
}
function readU64(address) {
  // XXX return [value, success-bool]
  // XXX handle truncation by capping to maximum JS value
  return [0, false];
}
const readUint = readU32, readUlong = readU32;

function toUlong(long) {
  return long >>> 0; // Coerce to u32
}


class File {
  constructor() {
    this.refs = 1;
    this.flags = 0;
  }
  ref() {
    ++this.refs;
    return this;
  }
  unref() {
    if (--this.refs <= 0)
      this.dispose();
  }
  dispose() {}
}

class Console extends File {
  // XXX
  constructor(console) {
    this.console = console;
  }
}

class NullDevice extends File {
  // XXX
}

class Fd {
  constructor(file, flags) {
    this.file = file;
    this.flags = flags;
  }
  close() {
    this.file.unref();
    this.file = null;
  }
}

const fds = new class extends Array {
  constructor() {
    super();

    const console = __root.console;
    const initialFile = console !== undefined ? new Console(console)
                                              : new NullDevice();
    for (let i = 0; i < 3; ++i)
      this.push(new Fd(console, 0));
  }
  isValid(fd) {
    return fd >= 0 && fd < this.length && this[fd] !== null;
  }
};

const proc = {
  pid: 1,
  uid: 1,
  gid: 1,
  supplementaryGroups: [],
  nice: 0,
  umask: 0o002,
  persona: PER_LINUX,
};

const rlimits = new class {
  constructor() {
    this.map = [];
    const self = this;
    function addLimit(value) {
      const holder = { cur: value, max: value };
      self.map.push(holder);
      return holder;
    }
    this.cpu = addLimit(0xffffffff); // Not implemented, limit ignored
    this.fsize = addLimit(0xffffffff); // Not implemented, limit ignored
    this.data = addLimit(0xffffffff); // Not implemented, limit ignored
    this.stack = addLimit(0xffffff); // Not implemented, limit ignored
    this.core = addLimit(0); // We never dump, can be anything
    this.rss = addLimit(0xffffffff); // Not implemented, limit ignored
    this.nproc = addLimit(1); // Automatically observed, no fork/clone
    this.nofile = addLimit(010000); // Implemented, one greater than max fd
    this.memlock = addLimit(0xffff); // Not implemented, limit ignored
    this.as = addLimit(0xffffffff); // Not implemented, limit ignored
    this.locks = addLimit(0xffff); // Not implemented, limit ignored
    this.sigpending = addLimit(0xffff); // Not implemented, limit ignored
    this.msgqueue = addLimit(0xffffffff); // Not implemented, limit ignored
    this.nice = addLimit(20); // Implemented, not that it does anything...
    this.rtprio = addLimit(0); // Not implemented, limit ignored
  }
  getByIndex(idx) {
    return idx >= 0 && idx < this.map.length ? this.map[idx] : null;
  }
};


//
// The syscalls
//

function fdNotSock(fd) {
  return fds.isValid(fd) ? -ENOTSOCK : -EBADF;
}
function eNosys() {
  return -ENOSYS;
}
function eAfnosupport() {
  return -EAFNOSUPPORT;
}
function ePerm() {
  return -EPERM;
}

export { fdNotSock as __syscall_accept4 }; // No sockets in minscripten
export { fdNotSock as __syscall_accept }; // No sockets in minscripten

function adjtimex(tx) {
  const [mode, result] = readUint(tx);
  if (!result)
    return -EFAULT;
  if (mode !== 0 && mode !== ADJ_OFFSET_SS_READ)
    return -EPERM;
  if (!writeMem(tx, 0, sizeofTimex))
    return -EFAULT;
  return TIME_OK;
}
export function __syscall_adjtimex(tx) {
  tx = toUlong(tx);
  return adjtimex(tx);
}

export { ePerm as __syscall_chroot }; // Fails unless root

const getMonotonicTime;
const getMonotonicResolution;
let lastMonotonicTime = Number.MIN_VALUE, getMonotonicBias = 0;
if (__root.process !== undefined) {
  // Node.js
  getMonotonicTime = function() {
    const [seconds, nanos] = __root.process.hrtime();
    return seconds*1e3 + (nanos/1e6);
  };
  getMonotonicResolution = 1e3; // Guess that it's microsecond resolution
} else if (__root.performance) {
  getMonotonicTime = __root.performance.now;
  getMonotonicResolution = 1e3; // Guess that it's microsecond resolution
} else {
  getMonotonicTime = Date.now;
  getMonotonicResolution = 15e6; // Guess that it's 15ms resolution
}

export function __syscall_clock_adjtime(clockId, tx) {
  tx = toUlong(tx);
  if (clockId < CLOCK_REALTIME || clockId > CLOCK_MONOTONIC)
    return -EINVAL;
  return adjtimex(tx);
}
export function __syscall_clock_getres(clockId, ts) {
  const resolution;
  switch (clockId) {
  case CLOCK_REALTIME:
    resolution = 15e6; // Guess that it's 15ms resolution
    break;
  case CLOCK_MONOTONIC:
    resolution = getMonotonicResolution;
    break;
  default:
    return -EINVAL;
  }
  if (ts !== NULL) {
    const seconds = 0;
    const nanoseconds = Math.max(1, Math.min(resolution, 999999999));
    if (!writeTimet(ts, seconds) ||
        !writeLong(ts += sizeofTimet, nanoseconds))
      return -EFAULT;
  }
  return 0;
}
export function __syscall_clock_gettime(clockId, ts) {
  let now;
  switch (clockId) {
  case CLOCK_REALTIME:
    now = Date.now() / 1000;
    break;
  case CLOCK_MONOTONIC:
    // Ensure that the clock really does go forwards.
    now = getMonotonicTime() / 1000 + getMonotonicBias;
    if (now < lastMonotonicTime) {
      getMonotonicBias += lastMonotonicTime - now;
      now = lastMonotonicTime;
    }
    break;
  default:
    return -EINVAL;
  }
  const seconds = Math.floor(now);
  let nanoseconds = Math.floor((now - seconds) * 1e9;
  nanoseconds = Math.max(0, Math.min(nanoseconds, 999999999));
  if (!writeTimet(ts, seconds) ||
      !writeLong(ts += sizeofTimet, nanoseconds))
    return -EFAULT;
  return 0;
}
export { eNosys as __syscall_clock_nanosleep }; // No sleep in minscripten
export function __syscall_clock_settime(clockId, ts) {
  if (clockId < CLOCK_REALTIME || clockId > CLOCK_MONOTONIC)
    return -EINVAL;
  return -EPERM;
}

export { eNosys as __syscall_clone }; // No multiprocess in minscripten

export function __syscall_close(fd) {
  if (!fds.isValid(fd))
    return -EBADF;
  fds[fd].close();
  fds[fd] = null;
  return 0;
}

function dup3(oldFd, newFd, flags) {
  if (oldFd === newFd || (flags & ~O_CLOEXEC) !== 0)
    return -EINVAL;
  if (!fds.isValid(oldFd))
    return -EBADF;
  if (newFd >= rlimits.nofile.cur)
    return -EBADF;
  if (fds.isValid(newFd))
    fds[newFd].close();
  fds[newFd] = new Fd(fds[oldFd].file.ref(), flags);
  return newFd;
}
export function __syscall_dup2(oldFd, newFd) {
  if (oldFd === newFd) {
    return fds.isValid(newFd) ? newFd : -EBADF;
  }
  return dup3(oldFd, newFd, 0);
}
export function __syscall_dup3(oldFd, newFd, flags) {
  return dup3(oldFd, newFd, flags);
}
export function __syscall_dup(oldFd) {
  if (!fds.isValid(oldFd))
    return -EBADF;
  const newFd = nextFd();
  if (newFd >= rlimits.nofile.cur)
    return -EMFILE;
  fds[newFd] = new Fd(fds[oldFd].file.ref(), 0);
  return newFd;
}

export { eNosys as __syscall_execveat }; // No multiprocess in minscripten
export { eNosys as __syscall_execve }; // No multiprocess in minscripten

function exit(status) {
  throw new ExitException(status);
}
export { exit as __syscall_exit };
export { exit as __syscall_exit_group };

export { eNosys as __syscall_fork }; // No multiprocess in minscripten

export function __syscall_getcpu(cpu, node) {
  if (!writeUint(cpu, 0) || !writeUint(node, 0))
    return -EFAULT;
  return 0;
}

export function __syscall_getegid() {
  return proc.gid;
}
export function __syscall_geteuid() {
  return proc.uid;
}
export function __syscall_getgid() {
  return proc.gid;
}
export function __syscall_getgroups(count, list) {
  list = toUlong(list);
  if (count < 0)
    return -EINVAL;
  const gs = proc.supplementaryGroups;
  if (count) {
    if (count < gs.length)
      return -EINVAL;
    for (let g of gs) {
      if (!writeGidt(list, g))
        return -EFAULT;
      list += 4;
    }
  }
  return gs.length;
}

export { fdNotSock as __syscall_getpeername }; // No sockets in minscripten

export function __syscall_getpgid(pid) {
  if (pid !== 0 && pid !== proc.pid)
    return -ESRCH;
  return proc.pid;
}
export function __syscall_getpgrp() {
  return __syscall_getpgid(0);
}
export function __syscall_getpid() {
  return proc.pid;
}
export function __syscall_getppid() {
  return 0; // Returns zero in pid 1
}
export function __syscall_getpriority(which, who) {
  switch (which) {
  case PRIO_PROCESS:
  case PRIO_PGRP:
    if (who !== 0 && who !== proc.pid)
      return -ESRCH;
  case PRIO_USER:
    if (who !== 0 && who !== proc.uid)
      return -ESRCH;
  default:
    return -EINVAL;
  }
  return 20 - proc.nice;
}

export function __syscall_getrandom(buf, bufLen, flags) {
  bufLen = toUlong(bufLen);
  if ((flags & ~(GRND_NONBLOCK | GRND_RANDOM)) !== 0)
    return -EINVAL;
  const vals = new Uint8Array(256);
  const crypto = __root.crypto;
  let remLen = bufLen;
  while (remLen > 0) {
    crypto.getRandomValues(vals);
    const len = Math.min(bufLen, vals.length);
    if (!writeArray(buf, vals, len))
      return -EFAULT;
    remLen -= len;
    buf += len;
  }
  return bufLen;
}

export function __syscall_getrlimit(resource, rlim) {
  rlim = toUlong(rlim);
  const holder = rlimits.getByIndex(resource);
  if (holder === null)
    return -EINVAL;
  if (!writeUlong(rlim, holder.cur) ||
      !writeUlong(rlim += sizeofUong, holder.max))
    return -EFAULT;
  return 0;
}

export function __syscall_getrusage(who, ru) {
  ru = toUlong(ru);
  switch (who) {
  case RUSAGE_SELF:
  case RUSAGE_THREAD:
    if (!writeMem(ru, 0, sizeofRusage) ||
        !writeLong(ru + 2 * sizeofTimeval, memory.getUsage()))
      return -EFAULT;
    return 0;
  case RUSAGE_CHILDREN:
    if (!writeMem(ru, 0, sizeofRusage))
      return -EFAULT;
    return 0;
  default:
    return -EINVAL;
  }
}

export function __syscall_getresgid(rgid, egid, sgid) {
  if (!writeGidt(rgid, proc.gid) || !writeGidt(egid, proc.gid) ||
      !writeGidt(sgid, proc.gid))
    return -EFAULT;
  return 0;
}
export function __syscall_getresuid(ruid, euid, suid) {
  if (!writeUidt(ruid, proc.uid) || !writeUidt(euid, proc.uid) ||
      !writeUidt(suid, proc.uid))
    return -EFAULT;
  return 0;
}
export function __syscall_getsid(pid) {
  if (pid !== 0 && pid !== proc.pid)
    return -ESRCH;
  return proc.pid;
}
export function __syscall_gettid() {
  return proc.pid;
}

export { fdNotSock as __syscall_getsockname }; // No sockets in minscripten
export { fdNotSock as __syscall_getsockopt }; // No sockets in minscripten

export function __syscall_gettimeofday(tv, tz) {
  tv = toUlong(tv);
  tz = toUlong(tz);
  if (tv !== NULL) {
    const s = Date.now();
    if (!writeTimet(tv, s / 1000) ||
        !writeSusecondst(tv += sizeofTimet, (s % 1000) * 1000))
      return -EFAULT;
  }
  if (tz !== NULL && !writeMem(tz, 0, 2*sizeofInt)) // Obsolete struct, not used
    return -EFAULT;
  return 0;
}

export function __syscall_getuid() {
  return proc.uid;
}

function deliverSignal(sig) {
  if (sig === 0) // Does nothing
    return 0;
  // XXX check for installed handlers, etc, and dispatch the signal or terminate
  throw new SignalException(sig);
}
export function __syscall_kill(pid, sig) {
  if (pid === -1) // Does not signal the calling process on Linux
    return 0;
  if (pid < 0 && pid !== -proc.pid)
    return -ESRCH;
  if (pid > 0 && pid !== proc.pid)
    return -ESRCH;
  // Pid is zero (self) or matches current process:
  return deliverSignal(sig);
}

export { eNosys as __syscall_nanosleep }; // No sleep in minscripten
export { eNosys as __syscall_pause }; // No sleep in minscripten

export function __syscall_personality(persona) {
  persona = toUlong(persona);
  const oldPersona = proc.persona;
  if (persona !== 0xffffffff) {
    if ((persona & 0xffffff00) !== 0 || (persona & 0xff) !== PER_LINUX)
      return -EINVAL;
    proc.persona = persona;
  }
  return oldPersona;
}

export function __syscall_prlimit64(pid, resource, newLim, oldLim) {
  newLim = toUlong(newLim);
  oldLim = toUlong(oldLim);
  if (pid !== 0 && pid !== proc.pid)
    return -ESRCH;
  const holder = rlimits.getByIndex(resource);
  if (holder === null)
    return -EINVAL;
  const oldCur = holder.cur, oldMax = holder.max;
  if (newLim !== NULL) {
    let newCur, newMax, result;
    [newCur, result] = readU64(newLim);
    if (!result)
      return -EFAULT;
    [newMax, result] = readU64(newLim += 8);
    if (!result)
      return -EFAULT;
    if (newCur > newMax)
      return -EINVAL;
    if (newMax > holder.max)
      return -EPERM;
    holder.cur = newCur;
    holder.max = newMax;
  }
  if (oldLim !== NULL) {
    if (!writeU64(oldLim, oldCur) || !writeU64(oldLim += 8, oldMax))
      return -EFAULT;
  }
  return 0;
}

export { ePerm as __syscall_reboot }; // Fails unless root
export { ePerm as __syscall_setdomainname }; // Fails unless root

export function __syscall_setfsgid(fsgid) {
  return fsgid === proc.gid ? fsgid : -EPERM;
}
export function __syscall_setfsuid(fsuid) {
  return fsuid === proc.uid ? fsuid : -EPERM;
}
export function __syscall_setgid(gid) {
  return gid === proc.gid ? 0 : -EPERM;
}

export { ePerm as __syscall_setgroups }; // Fails unless root
export { ePerm as __syscall_sethostname }; // Fails unless root

export function __syscall_setpgid(pid, pgid) {
  if (pid !== 0 && pid !== proc.pid)
    return -ESRCH;
  if (pgid !== 0 && pgid !== proc.pid)
    return -EPERM;
  return 0;
}

export function __syscall_setpriority(which, who, nice) {
  switch (which) {
  case PRIO_PROCESS:
  case PRIO_PGRP:
    if (who !== 0 && who !== proc.pid)
      return -ESRCH;
  case PRIO_USER:
    if (who !== 0 && who !== proc.uid)
      return -ESRCH;
  default:
    return -EINVAL;
  }
  nice = Math.min(Math.max(-20, nice), 19);
  const niceRlim = 20 - nice;
  if (nice < proc.nice && niceRlim > rlimits.nice.cur)
    return -EACCESS;
  return 0;
}

export function __syscall_setregid(rgid, egid) {
  return (rgid === -1 || rgid === proc.gid) &&
         (egid === -1 || egid === proc.gid) ? 0 : -EPERM;
}
export function __syscall_setresgid(rgid, egid, sgid) {
  return (rgid === -1 || rgid === proc.gid) &&
         (egid === -1 || egid === proc.gid) &&
         (sgid === -1 || sgid === proc.gid) ? 0 : -EPERM;
}
export function __syscall_setresuid(ruid, euid, suid) {
  return (ruid === -1 || ruid === proc.uid) &&
         (euid === -1 || euid === proc.uid) &&
         (suid === -1 || suid === proc.uid) ? 0 : -EPERM;
}
export function __syscall_setreuid(ruid, euid) {
  return (ruid === -1 || ruid === proc.uid) &&
         (euid === -1 || euid === proc.uid) ? 0 : -EPERM;
}

export function __syscall_setrlimit(resource, rlim) {
  rlim = toUlong(rlim);
  const holder = rlimits.getByIndex(resource);
  if (holder === null)
    return -EINVAL;
  let newCur, newMax, result;
  [newCur, result] = readUlong(newLim);
  if (!result)
    return -EFAULT;
  [newMax, result] = readUlong(newLim += sizeofUlong);
  if (!result)
    return -EFAULT;
  if (newCur > newMax)
    return -EINVAL;
  if (newMax > holder.max)
    return -EPERM;
  holder.cur = newCur;
  holder.max = newMax;
  return 0;
}

export { ePerm as __syscall_setsid() }; // We are already a session group leader

export { fdNotSock as __syscall_setsockopt }; // No sockets in minscripten

export { ePerm as __syscall_settimeofday }; // Fails unless root

export function __syscall_setuid(uid) {
  return uid === proc.uid ? 0 : -EPERM;
}

export { fdNotSock as __syscall_shutdown }; // No sockets in minscripten
export { eAfnosupport as __syscall_socket }; // No sockets in minscripten

export function __syscall_times(tms) {
  tms = toUlong(tms);
  // Although the kernel's clock tick rate is configurable (at kernel
  // compile-time), that is *not* exposed via syscalls.  The Linux ABI specifies
  // that times() and other syscalls using clock_t shall tick at 100Hz.
  const USER_HZ = 100;
  if (tms !== NULL && !writeMem(tms, 0, 4 * sizeofClockt))
    return -EFAULT;
  return Date.now() / (1000 / USER_HZ);
}

export function __syscall_time(tloc) {
  tloc = toUlong(tloc);
  const s = Date.now() / 1000;
  if (tloc !== NULL && !writeTimet(tloc, s))
    return -EFAULT;
  return s;
}

function tkill(tgid, tid, sig) {
  if (tgid !== 0 && tgid !== proc.pid)
    return -ESRCH;
  if (tid !== proc.pid)
    return -ESRCH;
  return deliverSignal(sig);
}
export function __syscall_tkill(tid, sig) {
  if (pid <= 0)
    return -EINVAL;
  return tkill(0, tid, sig);
}
export function __syscall_tgkill(tgid, tid, sig) {
  if (tgid <= 0 || tid <= 0)
    return -EINVAL;
  return tkill(tgid, tid, sig);
}

export function __syscall_umask(mode) {
  const oldMode = proc.umask;
  proc.umask = mode & 0o777;
  return oldMode;
}

const utsSysname = "minscripten";
const utsNodename = "localhost";
const utsRelease = MINSCRIPTEN_RELEASE;
const utsVersion = "#" + MINSCRIPTEN_BUILD; // Matches Linux format
const utsMachine = "WebAssembly on JavaScript";
const utsDomainname = "";

export function __syscall_uname(uts) {
  uts = toUlong(uts);
  const UTS_LEN = 65;
  if (!writeString(uts, utsSysname, UTS_LEN) ||
      !writeString(uts += UTS_LEN, utsNodename, UTS_LEN) ||
      !writeString(uts += UTS_LEN, utsRelease, UTS_LEN) ||
      !writeString(uts += UTS_LEN, utsVersion, UTS_LEN) ||
      !writeString(uts += UTS_LEN, utsMachine, UTS_LEN) ||
      !writeString(uts += UTS_LEN, utsDomainname, UTS_LEN))
    return -EFAULT;
  return 0;
}

export { eNosys as __syscall_vfork }; // No multiprocess in minscripten

export { ePerm as __syscall_vhangup }; // Fails unless root

export { eNosys as __syscall_wait4 }; // No multiprocess/sleep in minscripten
export { eNosys as __syscall_waitid }; // No multiprocess/sleep in minscripten










// Do these soonish:
export { eNosys as __syscall_bind };
export { eNosys as __syscall_connect };
export { eNosys as __syscall_fcntl };
export { eNosys as __syscall_fstat };
export { eNosys as __syscall_ioctl };
export { eNosys as __syscall_listen };
export { eNosys as __syscall_lseek };
export { eNosys as __syscall_open };
export { eNosys as __syscall_poll };
export { eNosys as __syscall_read };
export { eNosys as __syscall_recvmmsg };
export { eNosys as __syscall_recvmsg };
export { eNosys as __syscall_readv };
export { eNosys as __syscall_recvfrom };
export { eNosys as __syscall_rt_sigprocmask };
export { eNosys as __syscall_sendmmsg };
export { eNosys as __syscall_sendmsg };
export { eNosys as __syscall_sendto };
export { eNosys as __syscall_stat };
export { eNosys as __syscall_socketpair };
export { eNosys as __syscall_writev };
export { eNosys as __syscall_write };


// I guess it would be nice to support pipes too with these:

export { eNosys as __syscall_pipe2 };
export { eNosys as __syscall_pipe };
export { eNosys as __syscall_tee };



// Then these:
export { eNosys as __syscall_access };
export { eNosys as __syscall_acct };
export { eNosys as __syscall_add_key };
export { eNosys as __syscall_alarm };
export { eNosys as __syscall_arch_prctl };
export { eNosys as __syscall_bpf };
export { eNosys as __syscall_capget };
export { eNosys as __syscall_capset };
export { eNosys as __syscall_chdir };
export { eNosys as __syscall_chmod };
export { eNosys as __syscall_chown };
export { eNosys as __syscall_copy_file_range };
export { eNosys as __syscall_creat };
export { eNosys as __syscall_delete_module };
export { eNosys as __syscall_epoll_create1 };
export { eNosys as __syscall_epoll_create };
export { eNosys as __syscall_epoll_ctl };
export { eNosys as __syscall_epoll_pwait };
export { eNosys as __syscall_epoll_wait };
export { eNosys as __syscall_eventfd2 };
export { eNosys as __syscall_eventfd };
export { eNosys as __syscall_faccessat };
export { eNosys as __syscall_fadvise64 };
export { eNosys as __syscall_fallocate };
export { eNosys as __syscall_fanotify_init };
export { eNosys as __syscall_fanotify_mark };
export { eNosys as __syscall_fchdir };
export { eNosys as __syscall_fchmodat };
export { eNosys as __syscall_fchmod };
export { eNosys as __syscall_fchownat };
export { eNosys as __syscall_fchown };
export { eNosys as __syscall_fdatasync };
export { eNosys as __syscall_fgetxattr };
export { eNosys as __syscall_finit_module };
export { eNosys as __syscall_flistxattr };
export { eNosys as __syscall_flock };
export { eNosys as __syscall_fremovexattr };
export { eNosys as __syscall_fsetxattr };
export { eNosys as __syscall_fstatfs };
export { eNosys as __syscall_fsync };
export { eNosys as __syscall_ftruncate };
export { eNosys as __syscall_futimesat };
export { eNosys as __syscall_getcwd };
export { eNosys as __syscall_getdents64 };
export { eNosys as __syscall_getdents };
export { eNosys as __syscall_getitimer };
export { eNosys as __syscall_get_mempolicy };
export { eNosys as __syscall_get_robust_list };
export { eNosys as __syscall_getxattr };
export { eNosys as __syscall_init_module };
export { eNosys as __syscall_inotify_add_watch };
export { eNosys as __syscall_inotify_init1 };
export { eNosys as __syscall_inotify_init };
export { eNosys as __syscall_inotify_rm_watch };
export { eNosys as __syscall_io_cancel };
export { eNosys as __syscall_io_destroy };
export { eNosys as __syscall_io_getevents };
export { eNosys as __syscall_ioprio_get };
export { eNosys as __syscall_ioprio_set };
export { eNosys as __syscall_io_setup };
export { eNosys as __syscall_io_submit };
export { eNosys as __syscall_kcmp };
export { eNosys as __syscall_kexec_file_load };
export { eNosys as __syscall_kexec_load };
export { eNosys as __syscall_keyctl };
export { eNosys as __syscall_lchown };
export { eNosys as __syscall_lgetxattr };
export { eNosys as __syscall_linkat };
export { eNosys as __syscall_link };
export { eNosys as __syscall_listxattr };
export { eNosys as __syscall_llistxattr };
export { eNosys as __syscall_lookup_dcookie };
export { eNosys as __syscall_lremovexattr };
export { eNosys as __syscall_lsetxattr };
export { eNosys as __syscall_lstat };
export { eNosys as __syscall_mbind };
export { eNosys as __syscall_membarrier };
export { eNosys as __syscall_memfd_create };
export { eNosys as __syscall_migrate_pages };
export { eNosys as __syscall_mincore };
export { eNosys as __syscall_mkdirat };
export { eNosys as __syscall_mkdir };
export { eNosys as __syscall_mknodat };
export { eNosys as __syscall_mknod };
export { eNosys as __syscall_mlock2 };
export { eNosys as __syscall_mlockall };
export { eNosys as __syscall_mlock };
export { eNosys as __syscall_modify_ldt };
export { eNosys as __syscall_mount };
export { eNosys as __syscall_move_pages };
export { eNosys as __syscall_mprotect };
export { eNosys as __syscall_mq_getsetattr };
export { eNosys as __syscall_mq_notify };
export { eNosys as __syscall_mq_open };
export { eNosys as __syscall_mq_timedreceive };
export { eNosys as __syscall_mq_timedsend };
export { eNosys as __syscall_mq_unlink };
export { eNosys as __syscall_msgctl };
export { eNosys as __syscall_msgget };
export { eNosys as __syscall_msgrcv };
export { eNosys as __syscall_msgsnd };
export { eNosys as __syscall_msync };
export { eNosys as __syscall_munlockall };
export { eNosys as __syscall_munlock };
export { eNosys as __syscall_name_to_handle_at };
export { eNosys as __syscall_newfstatat };
export { eNosys as __syscall_openat };
export { eNosys as __syscall_open_by_handle_at };
export { eNosys as __syscall_perf_event_open };
export { eNosys as __syscall_pivot_root };
export { eNosys as __syscall_ppoll };
export { eNosys as __syscall_prctl };
export { eNosys as __syscall_pread64 };
export { eNosys as __syscall_preadv2 };
export { eNosys as __syscall_preadv };
export { eNosys as __syscall_process_vm_readv };
export { eNosys as __syscall_process_vm_writev };
export { eNosys as __syscall_pselect6 };
export { eNosys as __syscall_ptrace };
export { eNosys as __syscall_pwrite64 };
export { eNosys as __syscall_pwritev2 };
export { eNosys as __syscall_pwritev };
export { eNosys as __syscall_quotactl };
export { eNosys as __syscall_readahead };
export { eNosys as __syscall_readlinkat };
export { eNosys as __syscall_readlink };
export { eNosys as __syscall_remap_file_pages };
export { eNosys as __syscall_removexattr };
export { eNosys as __syscall_renameat2 };
export { eNosys as __syscall_renameat };
export { eNosys as __syscall_rename };
export { eNosys as __syscall_request_key };
export { eNosys as __syscall_restart_syscall };
export { eNosys as __syscall_rmdir };
export { eNosys as __syscall_rt_sigaction };
export { eNosys as __syscall_rt_sigpending };
export { eNosys as __syscall_rt_sigqueueinfo };
export { eNosys as __syscall_rt_sigreturn };
export { eNosys as __syscall_rt_sigsuspend };
export { eNosys as __syscall_rt_sigtimedwait };
export { eNosys as __syscall_rt_tgsigqueueinfo };
export { eNosys as __syscall_sched_getaffinity };
export { eNosys as __syscall_sched_getattr };
export { eNosys as __syscall_sched_getparam };
export { eNosys as __syscall_sched_get_priority_max };
export { eNosys as __syscall_sched_get_priority_min };
export { eNosys as __syscall_sched_getscheduler };
export { eNosys as __syscall_sched_rr_get_interval };
export { eNosys as __syscall_sched_setaffinity };
export { eNosys as __syscall_sched_setattr };
export { eNosys as __syscall_sched_setparam };
export { eNosys as __syscall_sched_setscheduler };
export { eNosys as __syscall_sched_yield };
export { eNosys as __syscall_seccomp };
export { eNosys as __syscall_select };
export { eNosys as __syscall_semctl };
export { eNosys as __syscall_semget };
export { eNosys as __syscall_semop };
export { eNosys as __syscall_semtimedop };
export { eNosys as __syscall_sendfile };
export { eNosys as __syscall_setitimer };
export { eNosys as __syscall_set_mempolicy };
export { eNosys as __syscall_setns };
export { eNosys as __syscall_set_robust_list };
export { eNosys as __syscall_setxattr };
export { eNosys as __syscall_shmat };
export { eNosys as __syscall_shmctl };
export { eNosys as __syscall_shmdt };
export { eNosys as __syscall_shmget };
export { eNosys as __syscall_sigaltstack };
export { eNosys as __syscall_signalfd4 };
export { eNosys as __syscall_signalfd };
export { eNosys as __syscall_splice };
export { eNosys as __syscall_statfs };
export { eNosys as __syscall_swapoff };
export { eNosys as __syscall_swapon };
export { eNosys as __syscall_symlinkat };
export { eNosys as __syscall_symlink };
export { eNosys as __syscall_sync_file_range };
export { eNosys as __syscall_syncfs };
export { eNosys as __syscall_sync };
export { eNosys as __syscall_sysfs };
export { eNosys as __syscall_sysinfo };
export { eNosys as __syscall_syslog };
export { eNosys as __syscall_timer_create };
export { eNosys as __syscall_timer_delete };
export { eNosys as __syscall_timerfd_create };
export { eNosys as __syscall_timerfd_gettime };
export { eNosys as __syscall_timerfd_settime };
export { eNosys as __syscall_timer_getoverrun };
export { eNosys as __syscall_timer_gettime };
export { eNosys as __syscall_timer_settime };
export { eNosys as __syscall_truncate };
export { eNosys as __syscall_umount2 };
export { eNosys as __syscall_unlinkat };
export { eNosys as __syscall_unlink };
export { eNosys as __syscall_unshare };
export { eNosys as __syscall_userfaultfd };
export { eNosys as __syscall_ustat };
export { eNosys as __syscall_utimensat };
export { eNosys as __syscall_utimes };
export { eNosys as __syscall_utime };
export { eNosys as __syscall_vmsplice };

// Totally unimplemented, even upstream:
/*
__syscall_afs_syscall
__syscall_getpmsg
__syscall_putpmsg
__syscall_security
__syscall_tuxcall
*/