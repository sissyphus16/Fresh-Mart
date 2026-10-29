#!/bin/sh
cd "$(dirname "$0")"
javac -encoding UTF-8 GroceryShop.java || exit 1
( sleep 3; open http://localhost:8080 2>/dev/null || xdg-open http://localhost:8080 ) &
java GroceryShop
