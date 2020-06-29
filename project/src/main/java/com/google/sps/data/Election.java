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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Election {
  private String name;
  private Date date;
  private List<Position> candidatePositions;

  public Election(String inputName, Date inputDate, List<String> inputCandidateIds,
      List<String> inputCandidateNames, List<String> inputCandidatePositions,
      List<String> inputCandidatePartyAffiliation, List<String> inputCandidateIncumbency) {
    this.name = inputName;
    this.date = inputDate;
    // Reformat database data to correlate one {@code Position} object with a list of candidates
    // and their information.
    Set<String> distinctPositions = new HashSet<>(inputCandidatePositions);
    this.candidatePositions = new ArrayList<>(distinctPositions.size());
    for (String position : distinctPositions) {
      int startIndex = inputCandidatePositions.indexOf(position);
      int endIndex = inputCandidatePositions.lastIndexOf(position);
      Position candidatePosition = new Position(position,
                                                inputCandidateIds
                                                    .subList(startIndex, endIndex + 1),
                                                inputCandidateNames
                                                    .subList(startIndex, endIndex + 1),
                                                inputCandidatePartyAffiliation
                                                    .subList(startIndex, endIndex + 1),
                                                inputCandidateIncumbency
                                                    .subList(startIndex, endIndex + 1));
      this.candidatePositions.add(candidatePosition);
    }
  }

  private class Position {
    private String name;
    private List<String> candidateIds;
    private List<String> candidateNames;
    private List<String> candidatePartyAffiliation;
    private List<String> candidateIncumbency;

    Position (String inputName, List<String> inputCandidateIds, List<String> inputCandidateNames,
        List<String> inputCandidatePartyAffiliation, List<String> inputCandidateIncumbency) {
      this.name = inputName;
      this.candidateIds = inputCandidateIds;
      this.candidateNames = inputCandidateNames;
      this.candidatePartyAffiliation = inputCandidatePartyAffiliation;
      this.candidateIncumbency = inputCandidateIncumbency;
    }
  }
}
