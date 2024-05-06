import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter/services.dart';
import 'package:pointycastle/export.dart';
import 'package:video_player_android/video_player_android.dart';

class AesDataSource implements StreamDataSource{
  Dio? _dio;
  late int bytesToDrain;
  late StreamCipher cipher;
  late Stream<Uint8List> _stream;

  late Uint8List key;
  late Uint8List iv;

  AesDataSource(this.key, this.iv);

  @override
  Future<void> close() async {
    if (_dio == null){
      throw Exception("Stream is already closed");
    }
    _dio!.close();
    _dio = null;
  }

  @override
  Stream<Uint8List> getStream() async * {
    if (_dio == null){
      throw Exception("Stream was not opened before");
    }
    Uint8List buffer = Uint8List(0);
    await for(var chunk in _stream){
      buffer = Uint8List.fromList([...buffer, ...chunk]);
      if (buffer.length >= 16){
        var blocksCount = buffer.length ~/ 16;
        var encrypted = Uint8List.sublistView(buffer, 0, blocksCount * 16);
        var decrypted = cipher.process(encrypted);
        yield Uint8List.sublistView(decrypted, bytesToDrain);
        bytesToDrain = 0;
        buffer = Uint8List.sublistView(buffer, blocksCount * 16);
      }
    }
    
    if (buffer.isNotEmpty){
      var decrypted = cipher.process(Uint8List.sublistView(buffer, bytesToDrain));
      yield decrypted;
    }
  }

  @override
  Future<int> open(String uri, {int position = 0, int length = -1}) async {
    
    int start = position - (position % 16);
    int end = length == -1?-1: length + (16 - length % 16) + start - 1;

    _dio = Dio();
    var response = await _dio!.get(uri, options: Options(
      responseType: ResponseType.stream,
      headers: {
        "Range": "bytes=$start-${end == -1?'': end}"
      }
    ));
    if (response.statusCode! < 200 && response.statusCode!>=300){
      _dio = null;
      throw Exception("Status code for $uri is ${response.statusCode!}");
    }
    _stream = response.data.stream;
    int contentLength = int.parse(response.headers.map["content-length"]![0]);
    int blockOffset = start ~/ 16;
    
    Uint8List ivAdjusted = Uint8List.fromList(iv);
    ByteData byteData = ByteData.view(ivAdjusted.buffer);
    int counter = byteData.getUint64(8) + blockOffset;
    byteData.setUint64(8, counter);
    cipher = CTRStreamCipher(AESEngine())
      ..init(false, ParametersWithIV(KeyParameter(key), ivAdjusted));
    bytesToDrain = position - start;
    return contentLength;
  }
  
}


Uint8List bigIntToUint8List(BigInt bigInt) =>
  bigIntToByteData(bigInt).buffer.asUint8List();

ByteData bigIntToByteData(BigInt bigInt) {
  final data = ByteData((bigInt.bitLength / 8).ceil());
  var _bigInt = bigInt;

  for (var i = 1; i <= data.lengthInBytes; i++) {
    data.setUint8(data.lengthInBytes - i, _bigInt.toUnsigned(8).toInt());
    _bigInt = _bigInt >> 8;
  }

  return data;
}

BigInt bytesToBigInt(Uint8List bytes){
// Concatena los bytes de la lista en un solo valor BigInt
  BigInt result = BigInt.zero;
  for (int i = 0; i < bytes.length; i++) {
    result = result << 8; // Desplaza el valor BigInt 8 bits hacia la izquierda
    result = result + BigInt.from(bytes[i]); // Agrega el byte actual al valor BigInt
  }
  return result;
}

Uint8List fillOrTruncate(int size, Uint8List data, [int paddingNumber = 0, bool fillAtStart = true]){
  if (data.length == size){
    return data;
  }
  if (data.length < size){
    Uint8List result = Uint8List(size);
    int offset = 0;
    if (fillAtStart){
      result.setRange(size-data.length, size, data);
      offset = data.length;
    }
    else{
      result.setRange(0, data.length, data);
    }
    for (int i = data.length; i < size; i++){
      result[i-offset] = paddingNumber & 0xFF;
    }
    return result;
  }
  else{
    return data.sublist(0, size);
  }
}