/*
 Copyright (c) 2013 by Artur Andrzejak <arturuni@gmail.com>, Felix Langner, Silvestre Zabala

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.


 */

package edu.pvs.batchrunner.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * @author Artur Andrzejak
 * @author
 * @date: 18.01.2006 - 14:18:10
 * @description: Provides utility routines for time and date handling, such as conversions
 * @see
 */
public class TimeDate {

    private static final SimpleDateFormat defaultFormatOutput = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss");
    private static final SimpleDateFormat defaultFormatInput = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss", Locale.ENGLISH);
    private static String lastDatetimeFormat;
    private static final Date auxDate = new Date();

    private static final GregorianCalendar calendar = new GregorianCalendar();

    /** Number of miliseconds per hour */
    public static final long HOUR_FACTOR = 3600*1000;
    /** Number of miliseconds per day */
    public static final long DAY_FACTOR = 24*HOUR_FACTOR;
    /** Epoch of java, i.e 01.01.1970, 1:00, expressed as matlab serial date */
    public static final double MATLAB_EPOCH_START = 719529.0 + 1.0/24.0;

    /**
     * Converts a long timestamp to a human-readable format
     * If dateFormatString is null, len<=0, or equals to lastDatetimeFormat, last format is used (default: defaultFormatOutput)
     *
     * @param timestamp        timestamp of the date to be formatted; if <= 0, the current system time is taken
     * @param dateFormatString the format string according to the SimpleDateFormat rules, used only if not null and len>0 and != lastDatetimeFormat
     * @return the resulting string with human-readable time/date
     */
    public static synchronized String timestampToString(long timestamp, String dateFormatString) {
        if (timestamp < 0)
            timestamp = System.currentTimeMillis();
        SimpleDateFormat datetimeFormat = applyNewDatetimeFormat(dateFormatString, defaultFormatOutput);
        auxDate.setTime(timestamp);
        return datetimeFormat.format(auxDate);
    }

    /**
     * Converts a long timestamp to a human-readable format using the last specified format (default: defaultFormatOutput)
     *
     * @param timestamp        timestamp of the date to be formatted; if <= 0, the current system time is taken
     * @return the resulting string with human-readable time/date
     */
    public static synchronized String timestampToString(long timestamp) {
        return timestampToString(timestamp,  null);
    }



    /**
     * Converts a date string (human-readable) to timestamp
     * @param datetime         the time/date string from which we format
     * @param dateFormatString the format string indicating the format of the input  (according to the SimpleDateFormat rules, default is yyyy.MM.dd-HH.mm.ss)
     * @return the resulting timestamp in milisec since epoch
     */
    public static synchronized long stringToTimestamp(String datetime, String dateFormatString) {

        long timestamp = -1;
        try {
            timestamp = stringToTimestampWithException(datetime, dateFormatString);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return timestamp;
    }


    /**
     * Converts a date string (human-readable) to timestamp and possibly throws a parse exception
     * @param datetime         the time/date string from which we format
     * @param dateFormatString the format string indicating the format of the input  (according to the SimpleDateFormat rules, default is yyyy.MM.dd-HH.mm.ss)
     * @return the resulting timestamp in milisec since epoch
     */
    public static synchronized long stringToTimestampWithException(String datetime, String dateFormatString) throws ParseException {

        SimpleDateFormat datetimeFormat = applyNewDatetimeFormat(dateFormatString, defaultFormatInput);

        Date parsedDate = datetimeFormat.parse(datetime);
        long timestamp = parsedDate.getTime();
        return timestamp;
    }



    /**
     * Returns a string with current system date and time
     * If dateFormatString is null, len<=0, or equals to lastDatetimeFormat, last format is used (default: defaultFormatOutput)
     * @param dateFormatString the format string according to the SimpleDateFormat rules, used only if not null and len>0 and != lastDatetimeFormat
     * @return string with current system date and time
     */
    public static String getCurrentDateTime(String dateFormatString) {
        return timestampToString(-1, dateFormatString);
    }


    /**
     * Auxiliary: Sets up the in/output format for the timestamp
     * If dateFormatString is not null and longer >0, it is used, otherwise the datetimeFormat is used
     * @param dateFormatString the format string according to the SimpleDateFormat rules, used only if not null and len>0 and != lastDatetimeFormat
     * @param datetimeFormat the default format returned if the dateFormatString was not used
     */
    private static SimpleDateFormat applyNewDatetimeFormat(String dateFormatString, SimpleDateFormat datetimeFormat) {
        if (dateFormatString != null && dateFormatString.length() > 0) {
            if (!dateFormatString.equals(lastDatetimeFormat)) {
                datetimeFormat.applyPattern(dateFormatString);
                lastDatetimeFormat = dateFormatString;
            }
        }
        return datetimeFormat;
    }


    /** Converts matlab serialdate (= timestamp) to java long timestamp in miliseconds
     * Java's 0 == 01.01.1970, 1am (1:00)
     * Matlabs's 0 == 01.01.0000, 00:00, or 719529
     * @param serialDate date in matlab serialdate format
     * @return java long timestamp in miliseconds
     */
    public final static long matlab2javaTimestamp(double serialDate) {

        // offset to 01.01.1970 1:00
        serialDate = serialDate - MATLAB_EPOCH_START;
        if (serialDate < 0)
            throw new IllegalArgumentException("Trying to convert matlab serial date before 1.01.1970 1:00 to java timestamp, serialDate = "+serialDate);
        long result = (long) (serialDate * DAY_FACTOR);
        return result;
    }


    /** Converts java long timestamp in miliseconds to matlab serialdate (= timestamp) 
     * Java's 0 == 01.01.1970, 1am (1:00)
     * Matlabs's 0 == 01.01.0000, 00:00, or 719529
     * @param javaTimestamp java long timestamp in miliseconds
     * @return matlabs serialDate
     */
    public final static double java2MatlabTimestamp(long javaTimestamp) {

        double serialDate = ((double)javaTimestamp) / (double)DAY_FACTOR;
        // add offset to 01.01.1970 1:00
        serialDate = serialDate + MATLAB_EPOCH_START;
        return serialDate;
    }

    /** Returns day of the week of a long timestamp, codes according to java.util.Calendar:
     *  (SUN = 1, SAT = 7)
     * @param timestamp time in milisec's
     * @return day of the week of a long timestamp (SUN = 1, SAT = 7)
     */
    public synchronized final static int getDayOfWeek(long timestamp) {
        calendar.setTimeInMillis(timestamp);
        return calendar.get(Calendar.DAY_OF_WEEK);
    }
    
    public static void main(String[] args) {
        System.out.println( timestampToString(0+23*HOUR_FACTOR) );
    }

}
