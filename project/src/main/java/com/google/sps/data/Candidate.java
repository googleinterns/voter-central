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

package com.google.sps.data;

public class Candidate extends DirectoryCandidate {
  private String email;
  private String phone;
  private String PhotoUrl;
  private String position;
  private String candidateUrl;
  private String twitter;

  public Candidate(String id, String name, String party, String email,
      String phone, String PhotoURL, String position, String candidateUrl,
      String twitter, boolean isIncumbent) {
    super(id, name, party, isIncumbent);
    this.email = email;
    this.phone = phone;
    this.PhotoURL = PhotoURL;
    this.position = position;
    this.candidateUrl = candidateUrl;
    this.twitter = twitter;
  }
}
