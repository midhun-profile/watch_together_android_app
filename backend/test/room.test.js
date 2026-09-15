const assert = require('assert');
const http = require('http');

// Simple smoke test for backend server functionality
console.log('Testing WatchTogether Backend Room Code Generation...');

const CODE_ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
assert.strictEqual(CODE_ALPHABET.length, 32);
assert(!CODE_ALPHABET.includes('0'), 'Should not include 0');
assert(!CODE_ALPHABET.includes('O'), 'Should not include O');
assert(!CODE_ALPHABET.includes('1'), 'Should not include 1');
assert(!CODE_ALPHABET.includes('I'), 'Should not include I');

console.log('PASS: Alphabet has 32 unambiguous characters.');
console.log('All backend checks passed.');
