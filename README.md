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
Intermediate resource will be promptly released assuming that they have been adapted to this library.

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

The structure of Clojure collections are preserved by this library.
If you have some custom collection type that you want the library to understand, integrate it by adapting the `ITree` protocol

```clojure
(require '[lexref.tree :refer [ITree]])

(extent-type TYPE
  ITree
  (tree-vals VALUE-FUNC)
  (tree-map  MAP-FUNC))
```

where `VALUE-FUNC` is a function taking of one argument taking a tree and returning a flat sequence (not necessarily lazy) of values in the collection,
and `MAP-FUNC` is a structure preserving version of `map` for your `TYPE` (however with arguments reversed, since protocols dispatch on first argument).
All builtin collection types (or rather, should be) integrated.
Notably, `tree-map` for map collections only apply the function to the values and not the keys, which is most likely what you want.

## Installing

Install the library from source using Leiningen

    git clone https://github.com/cdeln/lexref-clj
    cd lexref-clj
    lein install

then in your project, assuming you are using Leiningen, add an entry `[lexref "0.1.0"]` to the `:dependencies` array.

## Examples

See [`lexref.python`](src/lexref/python.clj) for an example of how `release!` and `equals?` are specialized for numpy arrays in `libpython-clj`.
You can also browse the code in [`lexref.dev`](src/lexref/dev.clj), load it into your REPL and start to play around and get a feeling of how it works.

## Extending

This library is written to be extensible.
The `with-lexref` macro works by transforming an expression into a lexically referenced equivalent,
such that all intermediate instances of your type are promptly released.
The macro transforms expressions by dispatching on the list expression head symbol.
All builtin forms are (or rather, should be) supported as well as commonly used macros such as `let`.
By default, macros are not expanded unless explicitly told so,
either by binding `lexref.expr/*allow-macros*` context variable to `true`,
or by calling `(lexref.expr/allow-macro MACRO-SYMBOL)`.
In case a form needs special transformation you can specialize the behaviour by hooking into
the `lexref.expr/on-list-expr` multi method as follows

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
which is automatically handled by the library.

Finally, `lexref.core/with-lexref-sym` is a symbolic function version of the `with-lexref` macro,
which probably will come in handy if you write an extension.
In general, any useful macro should have a symbolc function version (which the macro should be implemented in terms of!),
in order to simplify meta programming.

## Contributing

Currently this library have only been adapted to `libpython-clj`.
Nevertheless, it is written to be customizable and easily integrated into other libraries as well.
If you adapt this library and want to share it, please open an issue to discuss how to integrate it.
If you discover a bug, experience some unexpected behaviour or have some other idea, please open an issue as well.
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
