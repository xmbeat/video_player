
import 'dart:typed_data';
import "dart:developer" as dev;
import 'package:video_player_android/video_player_android.dart';
import 'dart:async';
import 'package:dio/dio.dart';

class BufferedDataSource extends InputDataSource {
  Uint8List? _buffer;
  int _offset = 0;

  @override
  Future<void> close() async {
    _offset = 0;
  }

  @override
  Future<int> open(String uri, {int position = 0, int length = -1}) async {
    dev.log("== OPEN REACHED ${position} ==", name: "BUFFERED_DATA_SOURCE");
    if (_buffer != null){
      _offset = position;
      return _buffer!.length - position;
    }
    Dio dio = Dio();
    var response = await dio.get(uri, options: Options(
      responseType: ResponseType.stream,
    ));
    if (response.statusCode! < 200 && response.statusCode!>=300){
      throw Exception("Status code for ${uri} is ${response.statusCode!}");
    }
    Stream<Uint8List> stream = response.data.stream;
    int contentLength = int.parse(response.headers.map["content-length"]![0]);
    _buffer = Uint8List(contentLength);
    int pos = 0;
    await for (var chunk in stream){
      _buffer!.setRange(pos, pos+chunk.length, chunk);
      pos+=chunk.length;
    }
    _offset = position;
    return contentLength - position;
  }

  @override
  Future<Uint8List> read(int readLength) async {
     dev.log("== READ REACHED ${readLength} ==", name: "BUFFERED_DATA_SOURCE");
    if (_buffer==null){
      throw Exception("Source is not opened");
    }
    if (_offset>=_buffer!.length){
      throw Exception("End of File Reached");
    }
    if (_offset + readLength < _buffer!.length){
      Uint8List data = Uint8List(readLength);
      data.setRange(0, data.length, _buffer!, _offset);
      _offset+=data.length;
      return data;
    }
    else{
      Uint8List data = Uint8List(_buffer!.length - _offset);
      data.setRange(0, data.length, _buffer!, _offset);
      _offset+=data.length;
      return data;
    }
  }

}
