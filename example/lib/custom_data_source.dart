import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:video_player_android/video_player_android.dart';
import 'dart:developer' as dev;
class NetworkDataSource implements StreamDataSource{
  Dio? _dio;
  Stream<Uint8List>? _stream;
  @override
  Future<void> close() async {
    if (_dio == null){
      throw Exception("Stream is already closed");
    }
    _dio!.close();
  }

  @override
  Stream<Uint8List> getStream() async * {
    if (_stream == null){
      throw Exception("Stream was not opened before");
    }
    var offset = 0;
    await for(var chunk in _stream!){
      // dev.log("Read bytes ${chunk.length} offset $offset", name: "NetworkDataSource");
      offset += chunk.length;
      yield chunk;
    }
    // dev.log("EOF STREAM", name: "NetworkDataSource");
  }

  @override
  Future<int> open(String uri, {int position = 0, int length = -1}) async {
    _dio = Dio();
    var response = await _dio!.get(uri, options: Options(
    responseType: ResponseType.stream,
    headers: {
      "Range": "bytes=$position-${length>0?position+length-1:''}"
    }
    ));
    if (response.statusCode! < 200 && response.statusCode!>=300){
      throw Exception("Status code for ${uri} is ${response.statusCode!}");
    }
    _stream = response.data.stream;
    int contentLength = int.parse(response.headers.map["content-length"]![0]);
    // dev.log("Opening from: $position to $length, length: $contentLength", name: "NetworkDataSource");
    return contentLength;
  }
  
}