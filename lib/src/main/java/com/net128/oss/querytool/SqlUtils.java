package com.net128.oss.querytool;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlUtils {
    public static String replaceFunctionCalls(String input, String functionName,
           String prefix, String infix, String suffix) {
        var pos = 0;
        do {
            var result = replaceFunction(input, pos, functionName, prefix, infix, suffix);
            if(result == null || pos>=result.nextPos) break;
            input = result.modifiedInput;
            pos = result.nextPos;
        } while(true);
        return input;
    }

    private static ReplacementResult replaceFunction(String input, int pos,
            String functionName, String prefix, String infix, String suffix) {
        int[] functionPositions = findFunction(input, functionName, pos);
        if (functionPositions == null) return null;

        var functionStart = functionPositions[0];
        var functionEnd = functionPositions[1];
        var functionArguments = input.substring(functionStart + functionName.length() + 1, functionEnd - 1);
        var newFunctionCall = prefix + "(" + infix + functionArguments + suffix + ")";
        var modifiedInput = input.substring(0, functionStart) + newFunctionCall + input.substring(functionEnd);
        return new ReplacementResult(modifiedInput, functionStart + newFunctionCall.length());
    }

    private static int[] findFunction(String input, String functionName, int fromIndex) {
        var functionStart = input.indexOf(functionName + "(", fromIndex);
        if (functionStart == -1) {
            return null;
        }

        var openParenIndex = functionStart + functionName.length();
        var bracketCount = 0;
        var insideFunction = false;
        var endIndex = -1;

        for (var i = openParenIndex; i < input.length(); i++) {
            var currentChar = input.charAt(i);
            if (currentChar == '(') {
                bracketCount++;
                insideFunction = true;
            } else if (currentChar == ')') {
                bracketCount--;
            }

            if (insideFunction && bracketCount == 0) {
                endIndex = i + 1;
                break;
            }
        }

        if (endIndex == -1) return null;
        return new int[]{functionStart, endIndex};
    }

    @Data
    @AllArgsConstructor
    private static class ReplacementResult {
        String modifiedInput;
        int nextPos;
    }
}
