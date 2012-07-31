var strife = {};

strife.conflict_form = {};

strife.conflict_form.presubmit = function(e) {
  var inputs = {};
  $("#teams").find("input").each(function(i, input) {
    input = $(input);
    inputs[input.attr('name')] = input;
  });
  var username_keys = _.filter(_.keys(inputs), function(name) {return /team-\d+-\d+-username$/.test(name);});
  _.each(username_keys, function(name) {
    var u_input = inputs[name];
    if (u_input.val() === "") {
      u_input.closest(".control-group").remove();
    }
  });
};

strife.conflict_form.setup = function() {
  var form = $("#conflict-form");
  if (form.length === 1) {
    form.on("submit", strife.conflict_form.presubmit);
  }
};

strife.signout_link = {};

strife.signout_link.setup = function() {
  var signout_link = $(".signout-link");
  if (signout_link.length === 1) {
    signout_link.on("click", function(e) {
      e.preventDefault();
      var form_el = $("<form method='POST' action='/user/signout'></form>");
      $("body").append(form_el);
      form_el.submit();
    });
  }
};

strife.setup = function() {
  strife.conflict_form.setup();
  strife.signout_link.setup();
};

$(strife.setup);
