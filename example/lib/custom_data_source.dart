
import 'dart:math';
import 'dart:typed_data';

import 'package:video_player_android/video_player_android.dart';
import 'dart:async';
import 'package:dio/dio.dart';

class CustomDataSource extends InputDataSource {
  Dio?_dio;
  Stream<Uint8List> ?_inputStream;
  int _readLength = 0;
  int _totalChunkSizes = 0;
  List<Uint8List> _chunks = [];
  StreamSubscription<Uint8List>? _subscription;
  Completer<void> _completer = Completer();
  
  @override
  Future<void> close() async {
    Completer<void> completer = new Completer<void>();
    if (_dio!=null){
      await _subscription?.cancel();
      _dio!.close();
      _dio = null;
      _subscription = null;
      completer.complete();
    }
    else{
      completer.completeError(Exception("Connection is already closed"));
    }
    return completer.future;
  }

  @override
  Future<int> open(String uri, {int position = 0, int length = -1}) async {
    _dio = Dio();
    var response = await _dio!.get(uri, options: Options(
      responseType: ResponseType.stream,
      headers: {
        "Range": "bytes-$position-${length>0?position+length-1:''}"
      }
    ));
    if (response.statusCode! < 200 && response.statusCode!>=300){
      throw Exception("Status code for ${uri} is ${response.statusCode!}");
    }
    _completer = new Completer();
    _inputStream = response.data.stream;
    _subscription = _inputStream!.listen((chunk) {
      _chunks.add(chunk);
      _totalChunkSizes += chunk.length;
      if (_totalChunkSizes >= _readLength){
        _subscription?.pause();
        _completer.complete();
      } 
    }, onDone: () {
      _subscription?.cancel();
      _completer.complete();
    }, onError: (e){
      _completer.completeError(e);
      _subscription?.cancel();
    });

    int contentLength = int.parse(response.headers.map["content-length"]![0]);
    return contentLength;
  }

  Uint8List _readFromChunks(int length){
    Uint8List data = Uint8List(min(length, _totalChunkSizes));
    int pos = 0;
    int i = 0;
    for (; i < _chunks.length; i++){
      if (pos + _chunks[i].length > data.length){
        data.setRange(pos, data.length, _chunks[i]);
        break;
      }
      else{
        data.setRange(pos, pos + _chunks[i].length, _chunks[i]);
        pos+=_chunks[i].length;
      }
    }
    if (length >= _totalChunkSizes){
      _totalChunkSizes = 0;
      _chunks = [];
    }
    else{
      _totalChunkSizes = _totalChunkSizes - data.length;
      _chunks = [_chunks[i].sublist(_chunks[i].length - _totalChunkSizes)];
    }
    return data;
  }

  Future<Uint8List> _readFromStream(int length) async{
    _completer = new Completer();
    _readLength = length;
    _subscription?.resume();
    await _completer.future;
    return _readFromChunks(length);
  }

  @override
  Future<Uint8List> read(int readLength) async {
    Completer<Uint8List> completer = Completer<Uint8List>();
    if (_subscription==null){
      completer.completeError(new Exception("Stream is not opened"));
      return completer.future;
    }
    if (_totalChunkSizes >= readLength){
      Uint8List data = _readFromChunks(readLength);
      completer.complete(data);
    }
    else{
      Uint8List data = await _readFromStream(readLength);
      completer.complete(data);
    }
    return completer.future;
  }

}

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
    await for(var chunk in _stream!){
      yield chunk;
    }
  }

  @override
  Future<int> open(String uri, {int position = 0, int length = -1}) async {
    _dio = Dio();
    var response = await _dio!.get(uri, options: Options(
    responseType: ResponseType.stream,
    headers: {
      "Range": "bytes-$position-${length>0?position+length-1:''}"
    }
    ));
    if (response.statusCode! < 200 && response.statusCode!>=300){
      throw Exception("Status code for ${uri} is ${response.statusCode!}");
    }
    _stream = response.data.stream;
    int contentLength = int.parse(response.headers.map["content-length"]![0]);
    return contentLength;
  }
  
}