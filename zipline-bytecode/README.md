Zipline Bytecode
================

This module can read and write the QuickJS objects produced with `JS_WriteObject()`. This is the
same encoding used by `QuickJs.compile()`.


Limitations
-----------

The QuickJS serialized object format is undocumented and subject to change. The structure of the
serialized data implicitly depends on build flags (such as `CONFIG_ATOMICS` and `CONFIG_BIGNUM`).

This module supports the exact QuickJS version that builds with this project.


Updating QuickJS
----------------

Build with `DUMP_READ_OBJECT` to see a human-readable interpretation of the encoded data. The
following features are most likely to get out of sync:

 * Atoms are built-in well-known strings. Get the list by defining `DUMP_ATOMS`, which is a subset
   of the lines in `quickjs-atom.h`. Keep this in sync with `AtomSet.BUILT_IN_ATOMS`.

 * Flags in `JsFunctionBytecode`, `JsVarDef`, and `JsClosureVar` must be kept in sync.
