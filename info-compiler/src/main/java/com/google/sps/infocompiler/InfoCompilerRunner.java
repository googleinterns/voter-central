// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps.infocompiler;

import java.io.IOException;

/**
  * The entry point of Google Compute Engine: Compiles location-specific information for
  * elections, positions and candidates.
  */
public class InfoCompilerRunner {

  public static void main(String[] args) throws IOException {
    InfoCompiler infoCompiler = new InfoCompiler();
    infoCompiler.compileInfo();
    System.out.println("InfoCompiler completed.");
  }
}
