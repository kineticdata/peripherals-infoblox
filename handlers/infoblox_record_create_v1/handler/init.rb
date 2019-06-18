require File.expand_path(File.join(File.dirname(__FILE__), 'dependencies'))

class InfobloxRecordCreateV1
  def initialize(input)
    # Set the input document attribute
    @input_document = REXML::Document.new(input)
    
    # Store the info values in a Hash of info names to values.
    @info_values = {}
    REXML::XPath.each(@input_document,"/handler/infos/info") { |item|
      @info_values[item.attributes['name']] = item.text  
    }
    
    # Retrieve all of the handler parameters and store them in a hash attribute
    # named @parameters.
    @parameters = {}
    REXML::XPath.match(@input_document, 'handler/parameters/parameter').each do |node|
      @parameters[node.attribute('name').value] = node.text.to_s
    end
  end
  
  def execute() 
    params = {
      'name' => @parameters['name'] + "." + @parameters['zone'],
      'ipv4addr' => @parameters['ip_address']
    }

    resource = RestClient::Resource.new(@info_values["host_url"], :user => @info_values['username'], :password => @info_values['password'], :verify_ssl => OpenSSL::SSL::VERIFY_NONE)

    begin 
      resp = resource['/wapi/v1.0/record:a'].post params.to_json, :content_type => "application/json"
    rescue RestClient::BadRequest => error
      raise StandardError, error.inspect
    end

    if @parameters['create_ptr_record'].downcase == "true"
      params = {
        'ptrdname' => @parameters['name'] + "." + @parameters['zone'],
        'ipv4addr' => @parameters['ip_address']
      }

      begin 
        resp = resource['/wapi/v1.0/record:ptr'].post params.to_json, :content_type => "application/json"
      rescue RestClient::BadRequest => error
        raise StandardError, error.inspect
      end
      
    end

    <<-RESULTS
    <results/>
    RESULTS
  end

  # Returns the 'At_AttachmentOne' field from a KS_ACC_Attachment record.  The record
  # is retrieved using the given customer_survey_instance_id, attachment_question_menu_label,
  # and survey_template_instance_id.  It first queries KS_SRV_SurveyQuestion with
  # the attachment_question_menu_label and survey_template_instance_id to retrieve
  # the instance id of the question.  It then queries the KS_SRV_QuestionAnswerJoin
  # form with the question instance id and the custom survey instance id to retrieve
  # the answer instance id.  It finally queries the KS_ACC_Attachment form with
  # the answer instance id to retrieve the attachment record.
  #
  # Raises an exception if a KS_SRV_SurveyQuestion record could not be found with
  # the attachment_question_menu_label and survey_template_instance_id parameters.
  #
  # ==== Parameters
  # * customer_survey_instance_id (String) - The 'instanceId' of the KS_SRV_CustomerSurvey_base
  #   record related to this submission.
  # * attachment_question_menu_label (String) - The menu label of the attachment
  #   question whose attachment we want to retrieve.
  # * survey_template_instance_id (String) - The 'SurveyInstanceID' of the KS_SRV_CustomerSurvey_base
  #   record related to this submission.  This parameter is used to retrieve question
  #   information about this service item.
  #
  def get_attachment(customer_survey_instance_id, attachment_question_menu_label, survey_template_instance_id)
    survey_question_entry = @@remedy_forms['KS_SRV_SurveyQuestion'].find_entries(
      :single,
      :conditions => [%|'Editor Label' = "#{attachment_question_menu_label}" AND 'SurveyInstanceID' = "#{survey_template_instance_id}"|],
      :fields     => ['instanceId']
    )
    # A question record on KS_SRV_SurveyQuestion could not be found with the given
    # attachment_question_menu_label and survey_template_instance_id parameters.
    if survey_question_entry.nil?
      raise("No question was found with given label #{attachment_question_menu_label}")
    end
    
    question_answer_entry = @@remedy_forms['KS_SRV_QuestionAnswerJoin'].find_entries(
      :single,
      :conditions => [%|'QuestioninstanceId' = "#{survey_question_entry['instanceId']}" AND 'CustomerSurveyInstanceID' = "#{customer_survey_instance_id}"|],
      :fields     => ['answerinstanceId']
    )
    # The question does not have a related KS_SRV_QuestionAnswerJoin record for
    # the current submission.
    return nil if question_answer_entry.nil?

    attachment_entry = @@remedy_forms['KS_ACC_Attachment'].find_entries(
      :single,
      :conditions => [%|'FormID' = "#{question_answer_entry['answerinstanceId']}"|],
      :fields     => ['At_AttachmentOne']
    )
    # The answer does not have a related KS_ACC_Attachment record.
    return nil if attachment_entry.nil?

    # Return the 'At_AttachmentOne' field from the KS_ACC_Attachment record.
    attachment_entry['At_AttachmentOne']
  end

  # Preinitialize expensive operations that are not task node dependent (IE
  # don't change based on the input parameters passed via xml to the #initialize
  # method).  This will very frequently utilize task info items to do things
  # such as pre-load a Remedy form or generate a Remedy proxy user.
  def preinitialize_on_first_load(input_document, form_names)
    # Unless this method has already been called...
    unless self.class.class_variable_defined?('@@preinitialized')
      # Initialize a remedy context (login) account to execute the Remedy queries.
      @@remedy_context = ArsModels::Context.new(
        :server         => @info_values['remedy_server'],
        :username       => @info_values['remedy_username'],
        :password       => @info_values['remedy_password'],
        :port           => @info_values['remedy_port'],
        :prognum        => @info_values['remedy_prognum'],
        :authentication => @info_values['remedy_authentication']
      )
      # Initialize the remedy forms that will be used by this handler.
      @@remedy_forms = form_names.inject({}) do |hash, form_name|
        hash.merge!(form_name => ArsModels::Form.find(form_name, :context => @@remedy_context))
      end
      # Store that we are preinitialized so that this method is not called twice.
      @@preinitialized = true
    end
  end
  
  # This is a template method that is used to escape results values (returned in
  # execute) that would cause the XML to be invalid.  This method is not
  # necessary if values do not contain character that have special meaning in
  # XML (&, ", <, and >), however it is a good practice to use it for all return
  # variable results in case the value could include one of those characters in
  # the future.  This method can be copied and reused between handlers.
  def escape(string)
    # Globally replace characters based on the ESCAPE_CHARACTERS constant
    string.to_s.gsub(/[&"><]/) { |special| ESCAPE_CHARACTERS[special] } if string
  end
  # This is a ruby constant that is used by the escape method
  ESCAPE_CHARACTERS = {'&'=>'&amp;', '>'=>'&gt;', '<'=>'&lt;', '"' => '&quot;'}
end
