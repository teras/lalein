grammar Args;

WS : [ \t\r\n] -> skip ;

FLOAT: INT '.' DIGITS;
INT: '-'? DIGITS;
fragment DIGITS: [0-9]+;

STRING: '"' ('\\' ["\\nrlt] | ~["\\\r\n])* '"' ;

args : arg (',' arg)*;

arg : FLOAT | INT | STRING ;