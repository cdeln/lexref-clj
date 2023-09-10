# LexRef

A Clojure library implementing lexical references.
The library is useful for evaluating expressions with high memory consumption.
The JVM does not give strict guarantees on the timing of resource cleanup of intermediate values,
which often have to be manually handled by the programmer.
This typically happens when interfacing with native memory outside of the JVM.
See this article for rationale and walkthrough: https://nextjournal.com/cdeln/reference-counting-in-clojure .

## Usage

To adapt a custom resource to be lexically referenced, require the `lexref.resource` namespace and
specialize the `release!` multi method for your type

```clojure
(require '[lexref.resource :refer release!])

(defmethod release! TYPE [self]
    RELEASE-LOGIC)
```

where `TYPE` is the value returned from calling `type` on your resource (this is not necessarily the name of the actual type since it can be overriden by setting the meta type field).

By default, lexref will compare objects using `identical?`.
If your resource type can share memory between objects, you might need to inform that by specializing
the `equals?` multi method

```clojure
(require '[lexref.resource :refer equals?])

(defmethod equals? [TYPE TYPE] [a b]
    EQUALITY-LOGIC)
```

Note that it dispatch on the type of both arguments, so you can further specialize it in case
resources can be shared between objects of different types.

See `lexref.python` for an example of how `release!` and `equals?` are specialized for numpy arrays in `libpython-clj`.

Finally, once your type is adapted you can evaluate expressions involving it using the `with-lexref` macro

```clojure
(with-lexref EXPR)
```

The macro will transform your expression into a lexically referenced equivalent, such that all intermediate instances of your type are promptly released.
The macro transforms expressions by dispatching on the list head symbols.
All builtin forms are (or rather, should be) supported as well as commonly used macros such as `let`.
By default, macros are not expanded unless explicitly told so,
either by binding `lexref.expr/*allow-macros*` context variable to `true`,
or by calling `(lexref.expr/allow-macro MACRO-SYMBOL)`.
In case a form needs special transformation you can specialize the behaviour by hooking into
the list expression transform multi method `lexref.expr/on-list-expr` as follows

```clojure
(require '[lexref.expr :refer [on-list-expr]])
(on-list-expr LIST-HEAD-SYMBOL HANDLER)
```

where `LIST-HEAD-SYMBOL` is the name of the form you want to implement an expression transform for.
The `HANDLER` should be a function taking two arguments

1. A list of arguments. This is tail of the current form, head being `LIST-HEAD-SYMBOL`
2. A body expression evaluating to the transformed expression

For example, let-expressions are implemented like this. See `lexref.core` for the implementation.

## Bugs, features and other stuff

If you discover a bug, experience some unexpected behaviour or have a feature request, please open an issue.
Currently this library have only been adapted to `libpython-clj`.
Nevertheless, it is written to be customizable and easily integrated into other libraries as well.
If you adapt this library and want to share it, please open an issue to discuss.
You can also contact me directly on the Clojurians Slack or Zulip. I'll create a channel/stream if it becomes necessary.

## License

Copyright © 2023 Carl Dehlin

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
