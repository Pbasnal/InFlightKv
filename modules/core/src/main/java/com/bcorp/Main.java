package com.bcorp;

import com.bcorp.codec.Codec;
import com.bcorp.codec.CodecProvider;
import com.bcorp.codec.StringCodec;

import java.util.List;
import java.util.Scanner;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Key: ");
        String key = scanner.nextLine();

        System.out.print("Value: ");
        String value = scanner.nextLine();

//        List<Codec<?>> codecList = List.of(new StringCodec());
//
//        KeyValueStore keyValueStore = new KeyValueStore();
//        CodecProvider codecProvider = new CodecProvider(codecList);
//        KeyValueStoreApi cacheApi = new KeyValueStoreApi(keyValueStore, codecProvider);

//        cacheApi.se
    }
}