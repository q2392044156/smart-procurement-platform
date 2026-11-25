package com.procuremind.platform.pojo;


public record SqlFilter(String clause, Object[] args) {
        public String literal() {
            StringBuilder out = new StringBuilder(clause.length() + 16);
            int next = 0;
            for (int i = 0; i < clause.length(); i++) {
                char c = clause.charAt(i);
                if (c == '?') out.append(args[next++]);
                else out.append(c);
            }
            return out.toString();
        }
    }
