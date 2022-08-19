/*
 * creature-monitor - watching norns from afar?
 * Written starting in 2022 by 20kdc
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

#pragma once

#include <stddef.h>

class CMObject {
public:
	CMObject() {}
	CMObject(const CMObject &) = delete;
	virtual ~CMObject() {}
	void queueDelete();
	static void performQueuedDeletions();
private:
	CMObject * _nextInDeleteQueue = NULL;
	static CMObject * _deleteQueue;
};

extern void cmPerformQueuedDeletions();

class CMSlice {
public:
	char * data;
	size_t length;
	CMSlice() {}
	CMSlice(const char * text) : data((char *) text), length(strlen(text)) {}
	CMSlice(char * data, size_t length) : data(data), length(length) {}
	CMSlice(const char * data, size_t length) : data((char *) data), length(length) {}
	CMSlice slice(size_t pos) {
		return CMSlice(data + pos, length - pos);
	}
	CMSlice slice(size_t pos, size_t len) {
		return CMSlice(data + pos, len);
	}
	CMSlice first(size_t len) { return slice(0, len); }
	CMSlice last(size_t len) { return slice(length - len, len); }
	bool operator ==(const CMSlice & other) {
		if (length != other.length) return false;
		return !memcmp(data, other.data, length);
	}
	bool operator !=(const CMSlice & other) {
		if (length != other.length) return true;
		return memcmp(data, other.data, length) != 0;
	}
};

class CMBuffer : public CMObject, public CMSlice {
public:
	CMBuffer(const char * data, size_t len);
	CMBuffer(const CMSlice & slice) : CMBuffer(slice.data, slice.length) {}
	~CMBuffer();
};

// Advances a slice's beginning to the start of the next line/whatever.
// Returns false if there isn't one.
// Note that if the split char is found at the very end of the slice, false is returned (there's no more actual data)
bool cmNextString(CMSlice & slice, CMSlice & content, char split);

