# LexRef

A Clojure library implementing scoped based automatic reference counting, or in short, lexical references.
The library is useful for evaluating expressions with off heap memory usage.
The JVM garbage collector does not give strict guarantees on the timing of resource cleanup of intermediate values,
which often have to be manually handled by the programmer.
See this article for detailed rationale and walkthrough: https://nextjournal.com/cdeln/reference-counting-in-clojure .
The library is currently in an experimental state.

## Usage

Evaluating an expression using automatic reference counting is as simple as using the `with-lexref` macro

```clojure
(require '[lexref.core :refer [with-lexref]])

(with-lexref EXPR)
```

where `EXPR` is the expression of interest.
Intermediate resource will be promptly assuming that they have been adapted to this library.

To adapt a custom resource to be lexically referenced, specialize the `release!` multi method for your type

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

## Examples

See [`lexref.python`](src/lexref/python.clj) for an example of how `release!` and `equals?` are specialized for numpy arrays in `libpython-clj`.
You can also browse the code in [`lexref.dev`](src/lexref/python.clj), load it into your REPL and start to play around and get a feeling of how it works.

## Extending

This library is written to be extensible by downstream consumers.
The `with-lexref` macro works by transforming an expression into a lexically referenced equivalent,
such that all intermediate instances of your type are promptly released.
The macro transforms expressions by dispatching on list expression head symbols.
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

If a list head symbol is not registred as above and is not a macro, then it is assumed to be a function,
which are all automatically handled by the library.

## Contributing

Currently this library have only been adapted to `libpython-clj`.
Nevertheless, it is written to be customizable and easily integrated into other libraries as well.
If you adapt this library and want to share it, please open an issue to discuss.
If you discover a bug, experience some unexpected behaviour or have an idea, please open an issue to discuss it.
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
